package com.wangxinchen.dawang.config;

import java.time.ZoneId;
import java.util.TimeZone;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    /**
     * 定时通知基于 LocalDateTime.now() 计算触发时刻，必须固定为 Asia/Shanghai，
     * 否则容器默认 UTC 会导致「设定 7:30 实际在 15:30 才触发 / 不触发」。
     * 部署侧另在 docker-compose 的 JAVA_OPTS 加 -Duser.timezone=Asia/Shanghai 兜底。
     */
    @PostConstruct
    public void initTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai")));
    }
}
