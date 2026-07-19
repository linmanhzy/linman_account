package com.wangxinchen.dawang.controller;

import com.wangxinchen.dawang.common.Result;
import com.wangxinchen.dawang.dto.MonthlyStats;
import com.wangxinchen.dawang.dto.RecordRequest;
import com.wangxinchen.dawang.dto.RecordResponse;
import com.wangxinchen.dawang.security.SecurityHelper;
import com.wangxinchen.dawang.service.RecordService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/records")
public class RecordController {
    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    @Operation(summary = "查询当前用户的收支记录（可按类型/月份筛选）")
    @GetMapping
    public Result<List<RecordResponse>> list(@RequestParam(required = false) String type,
                                             @RequestParam(required = false) String month) {
        return Result.ok(recordService.list(SecurityHelper.getCurrentUserId(), type, month));
    }

    @Operation(summary = "新增一条收支记录")
    @PostMapping
    public Result<RecordResponse> create(@Valid @RequestBody RecordRequest req) {
        return Result.ok(recordService.create(SecurityHelper.getCurrentUserId(), req));
    }

    @Operation(summary = "修改一条收支记录（仅本人）")
    @PutMapping("/{id}")
    public Result<RecordResponse> update(@PathVariable Long id, @Valid @RequestBody RecordRequest req) {
        return Result.ok(recordService.update(SecurityHelper.getCurrentUserId(), id, req));
    }

    @Operation(summary = "删除一条收支记录（仅本人）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        recordService.delete(SecurityHelper.getCurrentUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "查询某月收支汇总")
    @GetMapping("/stats/monthly")
    public Result<MonthlyStats> monthlyStats(@RequestParam String month) {
        return Result.ok(recordService.monthlyStats(SecurityHelper.getCurrentUserId(), month));
    }

    @Operation(summary = "导出当前用户的账本（format=excel 或 csv）")
    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = "excel") String format,
                       HttpServletResponse response) throws IOException {
        byte[] data = recordService.exportBytes(SecurityHelper.getCurrentUserId(), format);
        boolean isCsv = "csv".equalsIgnoreCase(format);
        response.setContentType(isCsv
                ? "text/csv;charset=utf-8"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"记账大王_records." + (isCsv ? "csv" : "xlsx") + "\"");
        response.getOutputStream().write(data);
        response.getOutputStream().flush();
    }
}
