package com.linman.account.dto;

import lombok.Data;

/**
 * 用户记录条数与系统上限，前端用于判断是否达到告警阈值。
 */
@Data
public class UserRecordQuota {
    private long count;
    private long max;
}
