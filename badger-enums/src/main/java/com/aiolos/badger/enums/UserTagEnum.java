package com.aiolos.badger.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserTagEnum {

    EXISTING_USER((long) Math.pow(2, 0), "是否是老用户", "tag_info_01"),
    IS_LIVE_STREAMER((long) Math.pow(2, 1), "是否是主播", "tag_info_01"),
    IS_RICH_USER((long) Math.pow(2, 2), "是否是富有的用户", "tag_info_01"),
    ;
    
    private long tag;
    private String desc;
    private String fieldName;
}
