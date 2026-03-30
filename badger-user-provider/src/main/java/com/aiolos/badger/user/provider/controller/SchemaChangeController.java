package com.aiolos.badger.user.provider.controller;

import com.aiolos.badger.user.provider.model.bo.SchemaAddColumnBO;
import com.aiolos.badger.user.provider.model.vo.SchemaChangeReportVO;
import com.aiolos.badger.user.provider.service.SchemaChangeService;
import com.aiolos.common.cloud.annotation.IgnoreAuth;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/schema")
@Tag(name = "用户表结构变更")
@AllArgsConstructor
public class SchemaChangeController {

    private final SchemaChangeService schemaChangeService;

    @PostMapping("/add-column")
    @IgnoreAuth
    public SchemaChangeReportVO addColumn(@RequestBody SchemaAddColumnBO request) {
        return schemaChangeService.addColumn(request);
    }
}
