package com.linman.account.service;

import com.linman.account.common.BizException;
import com.linman.account.dto.CreateUserRequest;
import com.linman.account.dto.ResetPasswordRequest;
import com.linman.account.dto.UpdateUserRequest;
import com.linman.account.dto.UserSummaryDto;
import com.linman.account.entity.Role;
import com.linman.account.entity.User;
import com.linman.account.entity.UserStatus;
import com.linman.account.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(UserService.class)
@ContextConfiguration(classes = {UserServiceTest.TestConfig.class})
class UserServiceTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User createAdmin() {
        User u = new User();
        u.setUsername("admin");
        u.setPasswordHash(passwordEncoder.encode("admin123"));
        u.setRole(Role.ADMIN);
        u.setStatus(UserStatus.ENABLED);
        return userRepository.save(u);
    }

    private User createNormalUser(String name) {
        User u = new User();
        u.setUsername(name);
        u.setPasswordHash(passwordEncoder.encode("123456"));
        u.setRole(Role.USER);
        u.setStatus(UserStatus.ENABLED);
        return userRepository.save(u);
    }

    private CreateUserRequest createReq(String username, String password, String role) {
        CreateUserRequest r = new CreateUserRequest();
        r.setUsername(username);
        r.setPassword(password);
        r.setRole(role);
        return r;
    }

    // ===== createUser =====

    @Test
    void createUser_success() {
        CreateUserRequest req = createReq("newuser", "pass123", "USER");
        UserSummaryDto dto = userService.createUser(req);

        assertThat(dto.getUsername()).isEqualTo("newuser");
        assertThat(dto.getRole()).isEqualTo("USER");
        assertThat(dto.getStatus()).isEqualTo("ENABLED");

        User saved = userRepository.findByUsername("newuser").orElseThrow();
        assertThat(passwordEncoder.matches("pass123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void createUser_duplicateUsername_throws() {
        createNormalUser("alice");
        CreateUserRequest req = createReq("alice", "pass456", "USER");

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名已存在");
    }

    // ===== updateUser =====

    @Test
    void updateUser_success() {
        User admin = createAdmin();
        User u = createNormalUser("oldname");
        UpdateUserRequest req = new UpdateUserRequest();
        req.setUsername("newname");
        req.setRole("ADMIN");

        UserSummaryDto dto = userService.updateUser(u.getId(), req, admin.getId());
        assertThat(dto.getUsername()).isEqualTo("newname");
        assertThat(dto.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void updateUser_partialUpdate_preservesUnchangedFields() {
        User admin = createAdmin();
        User u = createNormalUser("bob");
        UpdateUserRequest req = new UpdateUserRequest();
        req.setUsername("bobby");

        UserSummaryDto dto = userService.updateUser(u.getId(), req, admin.getId());
        assertThat(dto.getUsername()).isEqualTo("bobby");
        assertThat(dto.getRole()).isEqualTo("USER");
    }

    // ===== deleteUser =====

    @Test
    void deleteUser_success() {
        User admin = createAdmin();
        User u = createNormalUser("to_delete");
        String adminName = admin.getUsername();

        userService.deleteUser(u.getId(), admin.getId(), adminName);
        assertThat(userRepository.findById(u.getId())).isEmpty();
    }

    @Test
    void deleteUser_selfDeletion_throws() {
        User admin = createAdmin();
        assertThatThrownBy(() ->
                userService.deleteUser(admin.getId(), admin.getId(), admin.getUsername()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能删除自己的账户");
    }

    @Test
    void deleteUser_normalUser_success() {
        User admin = createAdmin();
        User other = createNormalUser("other");

        // 只有一个管理员时，可以删除普通用户
        userService.deleteUser(other.getId(), admin.getId(), admin.getUsername());
        assertThat(userRepository.findById(other.getId())).isEmpty();
    }

    @Test
    void deleteUser_lastEnabledAdmin_throws() {
        // 系统中仅有 1 个启用管理员时，禁止删除任何管理员账户（含已禁用的）
        User admin = createAdmin(); // 启用的管理员（操作者）
        User disabledAdmin = new User();
        disabledAdmin.setUsername("admin2");
        disabledAdmin.setPasswordHash(passwordEncoder.encode("123456"));
        disabledAdmin.setRole(Role.ADMIN);
        disabledAdmin.setStatus(UserStatus.DISABLED);
        User savedDisabledAdmin = userRepository.save(disabledAdmin);

        assertThatThrownBy(() ->
                userService.deleteUser(savedDisabledAdmin.getId(), admin.getId(), admin.getUsername()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("最后一个管理员");
    }

    // ===== resetPassword =====

    @Test
    void resetPassword_success() {
        User u = createNormalUser("charlie");
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setNewPassword("newpass456");

        userService.resetPassword(u.getId(), req);

        User updated = userRepository.findById(u.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("newpass456", updated.getPasswordHash())).isTrue();
    }

    // ===== listUsers =====

    @Test
    void listUsers_returnsAllUsers() {
        createAdmin();
        createNormalUser("u1");
        createNormalUser("u2");

        List<UserSummaryDto> list = userService.listUsers();
        assertThat(list).hasSize(3);
        assertThat(list).extracting("username")
                .contains("admin", "u1", "u2");
    }

    // ===== changeStatus =====

    @Test
    void changeStatus_disableNormalUser_success() {
        User admin = createAdmin();
        User u = createNormalUser("bob");

        UserSummaryDto dto = userService.changeStatus(u.getId(), "DISABLED", admin.getId());
        assertThat(dto.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void changeStatus_disableDefaultAdmin_throws() {
        User admin = createAdmin();
        User other = createNormalUser("other");

        assertThatThrownBy(() -> userService.changeStatus(admin.getId(), "DISABLED", other.getId()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("默认管理员账号不能被禁用");
    }

    @Test
    void changeStatus_selfDisable_throws() {
        User admin = createAdmin();

        assertThatThrownBy(() -> userService.changeStatus(admin.getId(), "DISABLED", admin.getId()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能禁用自己的账户");
    }

    @Test
    void changeStatus_lastEnabledAdmin_throws() {
        User onlyAdmin = new User();
        onlyAdmin.setUsername("otherAdmin");
        onlyAdmin.setPasswordHash(passwordEncoder.encode("123456"));
        onlyAdmin.setRole(Role.ADMIN);
        onlyAdmin.setStatus(UserStatus.ENABLED);
        User saved = userRepository.save(onlyAdmin);

        User operator = createNormalUser("operator");

        assertThatThrownBy(() -> userService.changeStatus(saved.getId(), "DISABLED", operator.getId()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能禁用系统中最后一个启用的管理员");
    }
}
