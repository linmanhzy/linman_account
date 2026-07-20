package com.wangxinchen.dawang.entity;

public enum Frequency {
    DAILY,         // 每天定时
    SPECIFIC_DATE, // 指定日期发送一次
    ONCE           // 仅一次（立即发送，不进入定时调度，保存为 disabled 记录）
}
