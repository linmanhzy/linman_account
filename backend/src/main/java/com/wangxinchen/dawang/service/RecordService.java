package com.wangxinchen.dawang.service;

import com.alibaba.excel.EasyExcel;
import com.wangxinchen.dawang.common.BizException;
import com.wangxinchen.dawang.dto.MonthlyStats;
import com.wangxinchen.dawang.dto.RecordExportRow;
import com.wangxinchen.dawang.dto.RecordRequest;
import com.wangxinchen.dawang.dto.RecordResponse;
import com.wangxinchen.dawang.dto.UserRecordQuota;
import com.wangxinchen.dawang.entity.Record;
import com.wangxinchen.dawang.repository.RecordRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecordService {
    private final RecordRepository recordRepository;

    public RecordService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    private static final long MAX_RECORDS_PER_USER = 50_000;

    public RecordResponse create(Long userId, RecordRequest req) {
        long count = recordRepository.countByUserId(userId);
        if (count >= MAX_RECORDS_PER_USER) {
            throw new BizException(400,
                    "记录已达上限（" + MAX_RECORDS_PER_USER + " 条），请导出数据后删除旧记录再继续记账。");
        }
        Record r = new Record();
        r.setUserId(userId);
        apply(r, req);
        r.setCreatedAt(LocalDateTime.now());
        return toDto(recordRepository.save(r));
    }

    public RecordResponse update(Long userId, Long id, RecordRequest req) {
        Record r = recordRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "记录不存在"));
        if (!r.getUserId().equals(userId)) {
            throw new BizException(403, "无权限修改该记录");
        }
        apply(r, req);
        return toDto(recordRepository.save(r));
    }

    public void delete(Long userId, Long id) {
        Record r = recordRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "记录不存在"));
        if (!r.getUserId().equals(userId)) {
            throw new BizException(403, "无权限删除该记录");
        }
        recordRepository.deleteById(id);
    }

    public List<RecordResponse> list(Long userId, String type, String month) {
        List<Record> records;
        if (month != null && !month.isBlank()) {
            LocalDate startDate = LocalDate.parse(month + "-01");
            LocalDate endDate = startDate.plusMonths(1).minusDays(1);
            if (type != null && !type.isBlank()) {
                records = recordRepository.findByUserIdAndTypeAndRecordDateBetweenOrderByRecordDateDescIdDesc(
                        userId, type, startDate, endDate);
            } else {
                records = recordRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateDescIdDesc(
                        userId, startDate, endDate);
            }
        } else if (type != null && !type.isBlank()) {
            records = recordRepository.findByUserIdAndTypeOrderByRecordDateDescIdDesc(userId, type);
        } else {
            records = recordRepository.findByUserIdOrderByRecordDateDescIdDesc(userId);
        }
        return records.stream().map(this::toDto).collect(Collectors.toList());
    }

    public MonthlyStats monthlyStats(Long userId, String month) {
        List<RecordResponse> records = list(userId, null, month);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (RecordResponse r : records) {
            if ("income".equals(r.getType())) {
                income = income.add(r.getAmount());
            } else if ("expense".equals(r.getType())) {
                expense = expense.add(r.getAmount());
            }
        }
        MonthlyStats s = new MonthlyStats();
        s.setIncome(income);
        s.setExpense(expense);
        s.setBalance(income.subtract(expense));
        return s;
    }

    private void apply(Record r, RecordRequest req) {
        if (!"income".equals(req.getType()) && !"expense".equals(req.getType())) {
            throw new BizException(400, "类型必须是 income 或 expense");
        }
        r.setType(req.getType());
        r.setAmount(req.getAmount());
        r.setRecordDate(req.getRecordDate());
        r.setCategoryL1(req.getCategoryL1());
        r.setCategoryL2(req.getCategoryL2());
        r.setNote(req.getNote());
    }

    private RecordResponse toDto(Record r) {
        RecordResponse d = new RecordResponse();
        d.setId(r.getId());
        d.setType(r.getType());
        d.setAmount(r.getAmount());
        d.setRecordDate(r.getRecordDate());
        d.setCategoryL1(r.getCategoryL1());
        d.setCategoryL2(r.getCategoryL2());
        d.setNote(r.getNote());
        d.setCreatedAt(r.getCreatedAt());
        return d;
    }

    // ===== 记录数与配额 =====

    public UserRecordQuota getUserRecordQuota(Long userId) {
        UserRecordQuota q = new UserRecordQuota();
        q.setCount(recordRepository.countByUserId(userId));
        q.setMax(MAX_RECORDS_PER_USER);
        return q;
    }

    // ===== 导出账本（仅本人数据） =====

    public byte[] exportBytes(Long userId, String format) {
        List<Record> records = recordRepository.findByUserIdOrderByRecordDateDescIdDesc(userId);
        if ("csv".equalsIgnoreCase(format)) {
            return buildCsv(records);
        }
        return buildExcel(records);
    }

    private byte[] buildExcel(List<Record> records) {
        List<RecordExportRow> rows = records.stream().map(this::toExportRow).collect(Collectors.toList());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, RecordExportRow.class).sheet("账目").doWrite(rows);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(500, "生成 Excel 失败：" + e.getMessage());
        }
    }

    private byte[] buildCsv(List<Record> records) {
        // 开头加 UTF-8 BOM，保证 Excel 打开中文不乱码
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("类型,金额,日期,一级分类,二级分类,备注,创建时间\n");
        for (Record r : records) {
            sb.append(csvField(typeLabel(r.getType()))).append(',')
                    .append(r.getAmount() == null ? "" : r.getAmount().toPlainString()).append(',')
                    .append(r.getRecordDate() == null ? "" : r.getRecordDate().toString()).append(',')
                    .append(csvField(r.getCategoryL1())).append(',')
                    .append(csvField(r.getCategoryL2())).append(',')
                    .append(csvField(r.getNote())).append(',')
                    .append(r.getCreatedAt() == null ? "" : r.getCreatedAt().toString())
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvField(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private RecordExportRow toExportRow(Record r) {
        RecordExportRow row = new RecordExportRow();
        row.setTypeLabel(typeLabel(r.getType()));
        row.setAmount(r.getAmount());
        row.setRecordDate(r.getRecordDate());
        row.setCategoryL1(r.getCategoryL1());
        row.setCategoryL2(r.getCategoryL2());
        row.setNote(r.getNote());
        row.setCreatedAt(r.getCreatedAt());
        return row;
    }

    private String typeLabel(String type) {
        if ("income".equals(type)) {
            return "收入";
        }
        if ("expense".equals(type)) {
            return "支出";
        }
        return type == null ? "" : type;
    }
}
