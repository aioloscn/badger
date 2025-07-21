package com.aiolos.badger.service.impl;

import com.aiolos.badger.model.po.AdminUser;
import com.aiolos.badger.mapper.AdminUserMapper;
import com.aiolos.badger.service.AdminUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 运营管理平台的admin级别用户 服务实现类
 * </p>
 *
 * @author Aiolos
 * @since 2025-07-21
 */
@Service
public class AdminUserServiceImpl extends ServiceImpl<AdminUserMapper, AdminUser> implements AdminUserService {

}
