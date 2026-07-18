package com.linman.account.dto;

import lombok.Data;

@Data
public class UnreadCountResponse {
    private long count;

    public static UnreadCountResponse of(long count) {
        UnreadCountResponse r = new UnreadCountResponse();
        r.setCount(count);
        return r;
    }
}
