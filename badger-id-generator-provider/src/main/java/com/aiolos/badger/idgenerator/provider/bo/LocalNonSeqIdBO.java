package com.aiolos.badger.idgenerator.provider.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ConcurrentLinkedQueue;

@Getter
@Setter
public class LocalNonSeqIdBO {

    @Schema(description = "主键")
    private int id;
    @Schema(description = "提前准备好的无序ID")
    private ConcurrentLinkedQueue<Long> idQueue;
    @Schema(description = "ID段的阈值")
    private Long nextThreshold;
    @Schema(description = "当前ID段的起始值")
    private Long currentStart;
}
