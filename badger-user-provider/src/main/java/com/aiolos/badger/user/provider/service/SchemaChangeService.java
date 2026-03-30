package com.aiolos.badger.user.provider.service;

import com.aiolos.badger.user.provider.model.bo.SchemaAddColumnBO;
import com.aiolos.badger.user.provider.model.vo.SchemaChangeReportVO;

public interface SchemaChangeService {

    SchemaChangeReportVO addColumn(SchemaAddColumnBO request);
}
