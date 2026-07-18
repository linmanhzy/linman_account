package com.linman.account.config;

import com.linman.account.entity.Role;
import com.linman.account.entity.User;
import com.linman.account.entity.UserStatus;
import com.linman.account.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${myapp.admin.username:admin}")
    private String adminUsername;

    @Value("${myapp.admin.password:admin123456}")
    private String adminPassword;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername(adminUsername)) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            admin.setStatus(UserStatus.ENABLED);
            admin.setCreatedAt(LocalDateTime.now());
            userRepository.save(admin);
            log.info("已创建默认管理员账户：{}（请尽快修改默认密码）", adminUsername);
        }
    }
}
