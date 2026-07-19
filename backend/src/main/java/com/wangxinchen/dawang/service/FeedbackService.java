package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.common.BizException;
import com.wangxinchen.dawang.dto.FeedbackRequest;
import com.wangxinchen.dawang.dto.FeedbackResponse;
import com.wangxinchen.dawang.dto.ReplyRequest;
import com.wangxinchen.dawang.entity.Feedback;
import com.wangxinchen.dawang.entity.User;
import com.wangxinchen.dawang.repository.FeedbackRepository;
import com.wangxinchen.dawang.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    public FeedbackService(FeedbackRepository feedbackRepository, UserRepository userRepository) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
    }

    public FeedbackResponse submit(Long userId, FeedbackRequest req) {
        Feedback f = new Feedback();
        f.setUserId(userId);
        f.setContent(req.getContent());
        f.setStatus("PENDING");
        f.setCreatedAt(LocalDateTime.now());
        f = feedbackRepository.save(f);
        return toDto(f, buildNameMap(List.of(f)));
    }

    public List<FeedbackResponse> myFeedbacks(Long userId) {
        List<Feedback> list = feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, String> nameMap = buildNameMap(list);
        return list.stream().map(f -> toDto(f, nameMap)).collect(Collectors.toList());
    }

    /* ===== 管理员 ===== */

    public List<FeedbackResponse> allFeedbacks() {
        List<Feedback> list = feedbackRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, String> nameMap = buildNameMap(list);
        return list.stream().map(f -> toDto(f, nameMap)).collect(Collectors.toList());
    }

    @Transactional
    public FeedbackResponse reply(Long feedbackId, ReplyRequest req) {
        Feedback f = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new BizException(404, "反馈不存在"));
        if (!"PENDING".equals(f.getStatus())) {
            throw new BizException(400, "该反馈已回复过");
        }
        f.setReply(req.getReply());
        f.setStatus("REPLIED");
        f = feedbackRepository.save(f);
        return toDto(f, buildNameMap(List.of(f)));
    }

    private Map<Long, String> buildNameMap(List<Feedback> feedbacks) {
        List<Long> userIds = feedbacks.stream()
                .map(Feedback::getUserId)
                .distinct()
                .collect(Collectors.toList());
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }

    private FeedbackResponse toDto(Feedback f, Map<Long, String> nameMap) {
        FeedbackResponse d = new FeedbackResponse();
        d.setId(f.getId());
        d.setContent(f.getContent());
        d.setStatus(f.getStatus());
        d.setReply(f.getReply());
        d.setCreatedAt(f.getCreatedAt());
        d.setUserId(f.getUserId());
        d.setUsername(nameMap.getOrDefault(f.getUserId(), "未知用户"));
        return d;
    }
}
