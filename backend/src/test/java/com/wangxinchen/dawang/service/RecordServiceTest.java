package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.dto.RecordRequest;
import com.wangxinchen.dawang.entity.Record;
import com.wangxinchen.dawang.repository.RecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordServiceTest {

    @Mock
    RecordRepository recordRepository;
    @Mock
    NotificationService notificationService;

    @InjectMocks
    RecordService recordService;

    private RecordRequest sampleRequest() {
        RecordRequest req = new RecordRequest();
        req.setType("expense");
        req.setAmount(new BigDecimal("12.34"));
        req.setRecordDate(LocalDate.now());
        return req;
    }

    @Test
    void create_firstBill_shouldTriggerFirstBillNotification() {
        // 该用户此前没有任何账单（count==0）→ 这条就是第一笔
        when(recordRepository.countByUserId(1L)).thenReturn(0L);
        Record saved = new Record();
        saved.setId(100L);
        when(recordRepository.save(any(Record.class))).thenReturn(saved);

        recordService.create(1L, sampleRequest());

        // §2.4：必须是首笔才发通知，且只发一次
        verify(notificationService, times(1)).sendFirstBillNotification(1L);
    }

    @Test
    void create_notFirstBill_shouldNotTriggerNotification() {
        // 该用户已有 5 条账单 → 这条不是第一笔
        when(recordRepository.countByUserId(1L)).thenReturn(5L);
        Record saved = new Record();
        saved.setId(101L);
        when(recordRepository.save(any(Record.class))).thenReturn(saved);

        recordService.create(1L, sampleRequest());

        verify(notificationService, never()).sendFirstBillNotification(anyLong());
    }

    @Test
    void create_invalidType_shouldThrow() {
        RecordRequest req = new RecordRequest();
        req.setType("invalid");
        req.setAmount(BigDecimal.ONE);
        req.setRecordDate(LocalDate.now());

        assertThrows(com.wangxinchen.dawang.common.BizException.class,
                () -> recordService.create(1L, req));
        verify(notificationService, never()).sendFirstBillNotification(anyLong());
    }
}
