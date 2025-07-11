package com.aiolos.badgeridgenerator.provider.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;

@Getter
@Setter
public class LocalSeqIdBO {
    
    @Schema(description = "主键")
    private int id;
    @Schema(description = "当前序号")
    private AtomicLong currentNum;
    @Schema(description = "ID段的阈值")
    private Long nextThreshold;
    @Schema(description = "当前ID段的起始值")
    private Long currentStart;
}
