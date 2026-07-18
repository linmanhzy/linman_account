package com.linman.account.service;

import com.linman.account.dto.FeedbackRequest;
import com.linman.account.dto.FeedbackResponse;
import com.linman.account.dto.ReplyRequest;
import com.linman.account.entity.Feedback;
import com.linman.account.entity.User;
import com.linman.account.repository.FeedbackRepository;
import com.linman.account.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    FeedbackRepository feedbackRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    FeedbackService feedbackService;

    @Test
    void submit_shouldSaveAndReturnDto() {
        FeedbackRequest req = new FeedbackRequest();
        req.setContent("建议增加按月汇总功能");

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        Feedback saved = new Feedback();
        saved.setId(100L);
        saved.setUserId(1L);
        saved.setContent(req.getContent());
        saved.setStatus("PENDING");

        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(user));
        when(feedbackRepository.save(any())).thenReturn(saved);

        FeedbackResponse resp = feedbackService.submit(1L, req);
        assertEquals(100L, resp.getId());
        assertEquals("PENDING", resp.getStatus());
        assertEquals("testuser", resp.getUsername());
    }

    @Test
    void reply_shouldUpdateStatusAndReply() {
        Feedback f = new Feedback();
        f.setId(1L);
        f.setUserId(1L);
        f.setContent("投诉");
        f.setStatus("PENDING");

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        ReplyRequest req = new ReplyRequest();
        req.setReply("已处理，谢谢反馈");

        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(f));
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(user));
        when(feedbackRepository.save(any())).thenReturn(f);

        FeedbackResponse resp = feedbackService.reply(1L, req);
        assertEquals("REPLIED", resp.getStatus());
        assertEquals("已处理，谢谢反馈", resp.getReply());
    }

    @Test
    void myFeedbacks_shouldReturnUserFeedbacks() {
        Feedback f = new Feedback();
        f.setId(1L);
        f.setUserId(1L);
        f.setContent("测试");
        f.setStatus("PENDING");

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        when(feedbackRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(f));
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(user));

        List<FeedbackResponse> list = feedbackService.myFeedbacks(1L);
        assertEquals(1, list.size());
        assertEquals("测试", list.get(0).getContent());
    }
}
