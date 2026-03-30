package com.aiolos.badger.service.impl;

import com.aiolos.badger.mapper.SchemaChangeAuditMapper;
import com.aiolos.badger.model.po.SchemaChangeAudit;
import com.aiolos.badger.service.SchemaChangeAuditService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class SchemaChangeAuditServiceImpl extends ServiceImpl<SchemaChangeAuditMapper, SchemaChangeAudit> implements SchemaChangeAuditService {
}
