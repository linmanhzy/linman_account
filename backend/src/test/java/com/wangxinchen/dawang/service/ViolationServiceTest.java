package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.entity.Notification;
import com.wangxinchen.dawang.entity.Role;
import com.wangxinchen.dawang.entity.User;
import com.wangxinchen.dawang.entity.UserViolation;
import com.wangxinchen.dawang.repository.NotificationRepository;
import com.wangxinchen.dawang.repository.UserRepository;
import com.wangxinchen.dawang.repository.UserViolationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ViolationService 单元测试（Mockito 风格）
 *
 * 关键不变量：
 * - count 从 1 累加；首次违规不发通知
 * - 达到阈值（2）且未通知 → 发通知给所有管理员，notified 置 true
 * - 已 notified=true 后再违规 → 不再发通知（窗口期内只通知一次）
 * - 距首次违规 > 7 天 → 重置 count=0、notified=false（窗口滑动）
 *
 * 通知内容必须包含违规用户名/ID/类型/次数，便于管理员排查。
 */
@ExtendWith(MockitoExtension.class)
class ViolationServiceTest {

    @Mock UserViolationRepository violationRepo;
    @Mock UserRepository userRepo;
    @Mock NotificationRepository notificationRepo;

    @InjectMocks ViolationService violationService;

    private UserViolation newViolation(Long userId, String type, int count, LocalDateTime firstAt) {
        UserViolation v = new UserViolation();
        v.setUserId(userId);
        v.setViolationType(type);
        v.setCount(count);
        v.setFirstAt(firstAt);
        v.setLastAt(firstAt);
        v.setNotified(false);
        return v;
    }

    private User adminUser(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setUsername(name);
        u.setRole(Role.ADMIN);
        return u;
    }

    @Test
    void firstViolation_shouldIncrementCountToOne_andNotNotifyAdmins() {
        // 第一次违规：repo 找不到已有记录 → 新建
        when(violationRepo.findByUserIdAndViolationType(1L, "BAD_PARAM")).thenReturn(Optional.empty());
        when(violationRepo.save(any(UserViolation.class))).thenAnswer(inv -> inv.getArgument(0));

        String msg = violationService.recordAndCheck(1L, "alice", "BAD_PARAM");

        ArgumentCaptor<UserViolation> saved = ArgumentCaptor.forClass(UserViolation.class);
        verify(violationRepo).save(saved.capture());
        assertEquals(1, saved.getValue().getCount());
        assertFalse(saved.getValue().isNotified(), "首次违规不应标记为已通知");
        assertNotNull(saved.getValue().getFirstAt(), "首次违规必须记录 firstAt");

        // 不应触发管理员通知
        verify(notificationRepo, never()).save(any());
        assertTrue(msg.contains("已被记录"), "返回提示应告知用户『已被记录』");
    }

    @Test
    void secondViolation_shouldHitThreshold_andNotifyAllAdmins() {
        // 已有一次违规（count=1，notified=false）
        UserViolation existing = newViolation(1L, "BAD_PARAM", 1, LocalDateTime.now().minusDays(1));
        when(violationRepo.findByUserIdAndViolationType(1L, "BAD_PARAM")).thenReturn(Optional.of(existing));
        when(violationRepo.save(any(UserViolation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findByRole(Role.ADMIN)).thenReturn(List.of(adminUser(10L, "root")));

        String msg = violationService.recordAndCheck(1L, "alice", "BAD_PARAM");

        assertEquals(2, existing.getCount(), "count 应累加到 2");
        assertTrue(existing.isNotified(), "达到阈值应标记为已通知");

        // 必须发通知给所有管理员
        ArgumentCaptor<Notification> note = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo, times(1)).save(note.capture());
        assertEquals(10L, note.getValue().getUserId(), "收件人应是管理员");
        assertTrue(note.getValue().getTitle().contains("违规"), "标题应明确『违规』");
        assertTrue(note.getValue().getContent().contains("alice"), "通知内容应包含违规用户名");
        assertTrue(note.getValue().getContent().contains("BAD_PARAM"), "通知内容应包含违规类型");
        assertTrue(note.getValue().getContent().contains("2"), "通知内容应包含当前次数");

        // 关键：service 在累加后 save 一次，达到阈值后 save 第二次 → 共 2 次
        verify(violationRepo, times(2)).save(any(UserViolation.class));

        assertTrue(msg.contains("通知管理员"), "达到阈值时提示应告知已通知管理员");
    }

    @Test
    void thirdViolation_shouldNotNotifyAgain_whenAlreadyNotified() {
        // 已有 count=2、notified=true（已通知过一次）
        UserViolation existing = newViolation(1L, "BAD_PARAM", 2, LocalDateTime.now().minusHours(2));
        existing.setNotified(true);
        when(violationRepo.findByUserIdAndViolationType(1L, "BAD_PARAM")).thenReturn(Optional.of(existing));
        when(violationRepo.save(any(UserViolation.class))).thenAnswer(inv -> inv.getArgument(0));

        String msg = violationService.recordAndCheck(1L, "alice", "BAD_PARAM");

        assertEquals(3, existing.getCount(), "count 应继续累加到 3");
        assertTrue(existing.isNotified(), "已通知过的标志应保持");

        // 关键：窗口期内只通知一次
        verify(notificationRepo, never()).save(any());
        verify(userRepo, never()).findByRole(any());

        assertFalse(msg.contains("通知管理员"), "已通知过的违规不应再包含『通知管理员』");
        assertTrue(msg.contains("已被记录"));
    }

    @Test
    void violationAfter7Days_shouldResetCount_andAllowNewNotification() {
        // 距首次违规 8 天（> 7 天窗口） → 重置
        UserViolation existing = newViolation(1L, "BAD_PARAM", 2, LocalDateTime.now().minusDays(8));
        existing.setNotified(true); // 上一窗口已通知过
        when(violationRepo.findByUserIdAndViolationType(1L, "BAD_PARAM")).thenReturn(Optional.of(existing));
        when(violationRepo.save(any(UserViolation.class))).thenAnswer(inv -> inv.getArgument(0));

        String msg = violationService.recordAndCheck(1L, "alice", "BAD_PARAM");

        // 重置后 count 从 0 起步 +1 = 1
        assertEquals(1, existing.getCount(), "窗口期外应重置为 1");
        assertFalse(existing.isNotified(), "新窗口应清空 notified 标志");

        // 单次违规不应发通知
        verify(notificationRepo, never()).save(any());
        assertTrue(msg.contains("已被记录"));
    }

    @Test
    void violationAtExactly7Days_shouldNotReset() {
        // 恰好 7 天 = 不大于 7，不应重置（用 > 7 比较）
        UserViolation existing = newViolation(1L, "BAD_PARAM", 2, LocalDateTime.now().minusDays(7));
        existing.setNotified(true);
        when(violationRepo.findByUserIdAndViolationType(1L, "BAD_PARAM")).thenReturn(Optional.of(existing));
        when(violationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        violationService.recordAndCheck(1L, "alice", "BAD_PARAM");

        assertEquals(3, existing.getCount(), "恰好 7 天不重置，count 应继续累加");
        assertTrue(existing.isNotified());
    }
}
