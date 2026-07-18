package com.linman.account.controller;

import com.linman.account.common.Result;
import com.linman.account.dto.FeedbackRequest;
import com.linman.account.dto.FeedbackResponse;
import com.linman.account.security.SecurityHelper;
import com.linman.account.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(summary = "提交反馈/建议/投诉")
    @PostMapping
    public Result<FeedbackResponse> submit(@Valid @RequestBody FeedbackRequest req) {
        return Result.ok(feedbackService.submit(SecurityHelper.getCurrentUserId(), req));
    }

    @Operation(summary = "查看自己的反馈列表（含管理员回复）")
    @GetMapping("/my")
    public Result<List<FeedbackResponse>> my() {
        return Result.ok(feedbackService.myFeedbacks(SecurityHelper.getCurrentUserId()));
    }
}
