package com.aiolos.badger.idgenerator.api;

public interface IdGeneratorApi {

    /**
     * 获取有序id
     * @param id 配置表主键
     * @return
     */
    Long getSeqId(Integer id);

    /**
     * 获取无序id
     * @param id 配置表主键
     * @return
     */
    Long getNonSeqId(Integer id);
}
