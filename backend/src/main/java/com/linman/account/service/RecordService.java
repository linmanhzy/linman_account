package com.linman.account.service;

import com.linman.account.common.BizException;
import com.linman.account.dto.MonthlyStats;
import com.linman.account.dto.RecordRequest;
import com.linman.account.dto.RecordResponse;
import com.linman.account.entity.Record;
import com.linman.account.repository.RecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    public RecordResponse create(Long userId, RecordRequest req) {
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
}
