package com.upi.IBS.scheduler;

import com.upi.IBS.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyLimitResetJobTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private DailyLimitResetJob dailyLimitResetJob;

    @Test
    void resetDailyLimit_Success() {
        // Given/When
        dailyLimitResetJob.resetDailyLimit();

        // Then
        verify(accountRepository, times(1)).resetDailyLimits();
    }

    @Test
    void resetDailyLimit_HandlesExceptionGracefully() {
        // Given
        doThrow(new RuntimeException("Database error")).when(accountRepository).resetDailyLimits();

        // When/Then
        assertDoesNotThrow(() -> dailyLimitResetJob.resetDailyLimit());
        verify(accountRepository, times(1)).resetDailyLimits();
    }
}
