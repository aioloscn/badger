package com.aiolos.badger.service.impl;

import com.aiolos.badger.model.po.UserEmailMapping;
import com.aiolos.badger.mapper.UserEmailMappingMapper;
import com.aiolos.badger.service.UserEmailMappingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户邮箱关联表 服务实现类
 * </p>
 *
 * @author Aiolos
 * @since 2025-07-21
 */
@Service
public class UserEmailMappingServiceImpl extends ServiceImpl<UserEmailMappingMapper, UserEmailMapping> implements UserEmailMappingService {

}
