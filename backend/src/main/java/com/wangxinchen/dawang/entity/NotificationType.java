package com.wangxinchen.dawang.entity;

public enum NotificationType {
    WELCOME,   // 首次登录欢迎（注册后第一次登录发送一次）
    DAILY,     // 每日定时通知
    HOLIDAY,   // 节假日通知
    EVENT,     // 事件通知（非固定时间节点，如第一笔账单）
    ADMIN      // 管理员手动群发
}
