package com.aiolos.badger.idgenerator.provider.rpc;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.json.JSONUtil;
import com.aiolos.badger.enums.exceptions.ServiceExceptionEnum;
import com.aiolos.badger.idgenerator.api.IdGeneratorApi;
import com.aiolos.badger.idgenerator.provider.bo.LocalNonSeqIdBO;
import com.aiolos.badger.idgenerator.provider.bo.LocalSeqIdBO;
import com.aiolos.badger.model.po.IdGenerateConfigPO;
import com.aiolos.badger.service.IdGenerateConfigService;
import com.aiolos.common.exception.utils.ExceptionUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@DubboService
public class IdGeneratorApiImpl implements IdGeneratorApi {

    @Resource
    private IdGenerateConfigService idGenerateConfigService;

    private static final float UPDATE_RATE = 0.75f;
    private final Map<Integer, LocalSeqIdBO> localSeqIdMap = new ConcurrentHashMap<>();
    private final Map<Integer, LocalNonSeqIdBO> localNonSeqIdMap = new ConcurrentHashMap<>();
    private final Map<Integer, Semaphore> semaphoreMap = new ConcurrentHashMap<>();
    // 不同配置可以并发更新
    private static final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(8, 16, 3, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000),
            r -> {
                Thread thread = new Thread(r);
                thread.setName("id-generator-thread-" + thread.getId());
                return thread;
            });

    @PostConstruct
    public void init() {
        List<IdGenerateConfigPO> idGenerateConfigList = idGenerateConfigService.list();
        idGenerateConfigList.forEach(po -> {
            tryUpdateSeqId(po, null);
            // 每个配置都初始化一个线程信号量，防止并发更新
            semaphoreMap.put(po.getId(), new Semaphore(1));
        });
    }

    @Override
    public Long getSeqId(Integer id) {
        if (id == null) {
            log.error("[getSeqId] id is null");
            return null;
        }
        LocalSeqIdBO localSeqIdBO = localSeqIdMap.get(id);
        if (localSeqIdBO == null) {
            log.error("[getSeqId] get local seq id but empty, config id: {}", id);
            return null;
        }

        long returnId = localSeqIdBO.getCurrentNum().getAndIncrement();
        if (returnId >= localSeqIdBO.getNextThreshold()) {
            log.error("[getSeqId] id is over limit, config id: {}", id);
            return null;
        }
        refreshLocalSeqId(localSeqIdBO);
        log.info("从有序队列取出: {}", returnId);
        return returnId;
    }

    @Override
    public Long getNonSeqId(Integer id) {
        if (id == null) {
            log.error("[getNonSeqId] id is null");
            return null;
        }
        LocalNonSeqIdBO localNonSeqIdBO = localNonSeqIdMap.get(id);
        if (localNonSeqIdBO == null) {
            log.error("[getNonSeqId] get local non-seq id but empty, config id: {}", id);
            return null;
        }

        Long nonSeqId = localNonSeqIdBO.getIdQueue().poll();
        if (nonSeqId != null) {
            if (nonSeqId >= localNonSeqIdBO.getNextThreshold()) {
                log.error("[getNonSeqId] id is over limit, config id: {}", id);
                return null;
            }
            refreshLocalNonSeqId(localNonSeqIdBO);
            log.info("从无序队列取出: {}", nonSeqId);
            return nonSeqId;
        } else {
            return emergencySyncIdQueue(localNonSeqIdBO);
        }
    }

    private Long emergencySyncIdQueue(LocalNonSeqIdBO bo) {
        Semaphore semaphore = semaphoreMap.get(bo.getId());
        if (semaphore == null) {
            log.error("[emergencySyncIdQueue] semaphore not found for config: {}", bo.getId());
            return null;
        }
        try {
            // 阻塞获取信号量
            semaphore.acquire();
            // 其他线程已更新id集合
            Long nonSeqId = bo.getIdQueue().poll();
            if (nonSeqId != null) return nonSeqId;

            IdGenerateConfigPO po = idGenerateConfigService.getById(bo.getId());
            if (po == null) {
                log.error("[emergencySyncIdQueue] config not found: {}", bo.getId());
                return null;
            }

            tryUpdateSeqId(po, bo);
            return bo.getIdQueue().poll();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            semaphore.release();
        }
    }

    private void refreshLocalSeqId(LocalSeqIdBO bo) {
        long step = bo.getNextThreshold() - bo.getCurrentStart();
        if (bo.getCurrentNum().get() - bo.getCurrentStart() > step * UPDATE_RATE) {
            asyncUpdateIdConfig(bo.getId());
        }
    }

    private void refreshLocalNonSeqId(LocalNonSeqIdBO bo) {
        long step = bo.getNextThreshold() - bo.getCurrentStart();
        if (bo.getIdQueue().size() < step * (1 - UPDATE_RATE)) {
            asyncUpdateIdConfig(bo.getId());
        }
    }

    private void asyncUpdateIdConfig(int id) {
        Semaphore semaphore = semaphoreMap.get(id);
        if (semaphore == null) {
            log.error("refreshLocalSeqId semaphore is null");
            return;
        }
        boolean acquire = semaphore.tryAcquire();
        if (acquire) {
            log.info("尝试将config id: {}进行本地同步", id);
            threadPoolExecutor.execute(() -> {
                try {
                    IdGenerateConfigPO po = idGenerateConfigService.getById(id);
                    if (po == null) {
                        log.error("config id: {}配置不存在", id);
                        return;
                    }
                    tryUpdateSeqId(po, null);
                } catch (Exception e) {
                    log.error("更新id配置失败, config id: {}", id, e);
                } finally {
                    semaphore.release();
                    log.info("config id: {}进行本地同步完成", id);
                }
            });
        }
    }

    private void tryUpdateSeqId(IdGenerateConfigPO po, LocalNonSeqIdBO bo) {
        int retry = 0;
        boolean updated = false;
        while (retry < 5 && !updated) {
            retry++;
            Long nextThreshold = po.getNextThreshold();
            Long currentStart = po.getCurrentStart();
            updated = idGenerateConfigService.lambdaUpdate()
                    .set(IdGenerateConfigPO::getNextThreshold, nextThreshold + po.getStep())
                    .set(IdGenerateConfigPO::getCurrentStart, currentStart + po.getStep())
                    .set(IdGenerateConfigPO::getVersion, po.getVersion() + 1)
                    .eq(IdGenerateConfigPO::getId, po.getId())
                    .eq(IdGenerateConfigPO::getVersion, po.getVersion())
                    .update();
            if (!updated) {
                log.warn("[tryUpdateSeqId] 更新失败，当前重试次数: {}", retry);
                ThreadUtil.sleep(Math.pow(2, retry) * 50 + ThreadLocalRandom.current().nextInt(50));
                po = idGenerateConfigService.getById(po.getId());
            } else {
                // 集群的话建议还是用无序，否则轮训获取到的id会跳跃
                if (po.getIsSeq() == 1) {
                    LocalSeqIdBO newBO = new LocalSeqIdBO();
                    newBO.setId(po.getId());
                    newBO.setCurrentNum(new AtomicLong(currentStart));
                    newBO.setNextThreshold(nextThreshold);
                    newBO.setCurrentStart(currentStart);
                    localSeqIdMap.put(po.getId(), newBO);
                    log.info("有序队列缓存ID: {}", JSONUtil.toJsonStr(localSeqIdMap));
                } else {
                    // 如果参数bo不为空，代表是以阻塞的方式更新的，只特换idQueue，根据happens-before原则其他线程能感知到变更
                    if (bo != null) {
                        buildIdQueue(po.getId(), bo, nextThreshold, currentStart);
                        log.info("阻塞更新无序队列ID集合: {}", JSONUtil.toJsonStr(bo));
                    } else {
                        LocalNonSeqIdBO newBO = new LocalNonSeqIdBO();
                        buildIdQueue(po.getId(), newBO, nextThreshold, currentStart);
                        localNonSeqIdMap.put(po.getId(), newBO);
                        log.info("无序队列缓存ID: {}", JSONUtil.toJsonStr(localNonSeqIdMap));
                    }
                }
            }
        }

        if (!updated) {
            ExceptionUtil.throwException(ServiceExceptionEnum.GET_ID_SECTION_ERROR);
        }
    }

    private void buildIdQueue(int configId, LocalNonSeqIdBO bo, Long nextThreshold, Long currentStart) {
        bo.setId(configId);
        bo.setNextThreshold(nextThreshold);
        bo.setCurrentStart(currentStart);
        List<Long> idList = new ArrayList<>();
        for (long i = currentStart; i < nextThreshold; i++) {
            idList.add(i);
        }
        Collections.shuffle(idList);
        ConcurrentLinkedQueue<Long> idQueue = new ConcurrentLinkedQueue<>(idList);
        bo.setIdQueue(idQueue);
    }
}
