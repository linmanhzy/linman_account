package com.wangxinchen.dawang.dto;

import lombok.Data;

@Data
public class LeaderboardEntry {
    private Long userId;
    private String username;
    private Integer bestScore;
    /** 是否为当前登录用户本人（用于前端高亮），非本人时 username 已被遮蔽 */
    private Boolean me = false;
}
