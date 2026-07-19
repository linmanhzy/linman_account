package com.wangxinchen.dawang.controller;

import com.wangxinchen.dawang.common.BizException;
import com.wangxinchen.dawang.common.Result;
import com.wangxinchen.dawang.dto.CategoryProportion;
import com.wangxinchen.dawang.dto.TrendPoint;
import com.wangxinchen.dawang.security.SecurityHelper;
import com.wangxinchen.dawang.service.ReportService;
import com.wangxinchen.dawang.service.ViolationService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reportService;
    private final ViolationService violationService;

    public ReportController(ReportService reportService, ViolationService violationService) {
        this.reportService = reportService;
        this.violationService = violationService;
    }

    @Operation(summary = "消费趋势：最近 N 个月每月收入与支出")
    @GetMapping("/trend")
    public Result<List<TrendPoint>> trend(@RequestParam(defaultValue = "12") int months) {
        return Result.ok(reportService.monthlyTrend(SecurityHelper.getCurrentUserId(), months));
    }

    @Operation(summary = "分类占比：按一级分类统计支出（或指定类型）占比；非法 type 将触发违规追踪")
    @GetMapping("/category-proportion")
    public Result<List<CategoryProportion>> categoryProportion(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String month) {
        Long userId = SecurityHelper.getCurrentUserId();
        if (type != null && !type.isBlank() && !"income".equals(type) && !"expense".equals(type)) {
            String warn = violationService.recordAndCheck(
                    userId, SecurityHelper.getCurrentUsername(), "INVALID_PARAM");
            throw new BizException(400, warn);
        }
        return Result.ok(reportService.categoryProportion(userId, type, month));
    }
}
