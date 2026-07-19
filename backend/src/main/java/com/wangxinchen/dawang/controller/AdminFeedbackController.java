package com.wangxinchen.dawang.controller;

import com.wangxinchen.dawang.common.Result;
import com.wangxinchen.dawang.dto.FeedbackResponse;
import com.wangxinchen.dawang.dto.ReplyRequest;
import com.wangxinchen.dawang.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/feedback")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeedbackController {
    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(summary = "管理员查看所有用户反馈")
    @GetMapping
    public Result<List<FeedbackResponse>> all() {
        return Result.ok(feedbackService.allFeedbacks());
    }

    @Operation(summary = "管理员回复某条反馈")
    @PutMapping("/{id}/reply")
    public Result<FeedbackResponse> reply(@PathVariable Long id, @Valid @RequestBody ReplyRequest req) {
        return Result.ok(feedbackService.reply(id, req));
    }
}
