package com.upi.IBS.service;

import com.upi.IBS.dto.request.CreditRequest;
import com.upi.IBS.dto.request.ReversalRequest;
import com.upi.IBS.dto.response.BankResponse;
import com.upi.IBS.entity.Account;
import com.upi.IBS.entity.EntryType;
import com.upi.IBS.entity.LedgerEntry;
import com.upi.IBS.exception.AccountNotFoundException;
import com.upi.IBS.exception.TransactionNotFoundException;
import com.upi.IBS.exception.DuplicateTransactionException;
import com.upi.IBS.repository.AccountRepository;
import com.upi.IBS.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private BankService bankService;

    private Account testAccount;
    private CreditRequest creditRequest;
    private UUID txnId;

    @BeforeEach
    void setUp() {
        txnId = UUID.randomUUID();
        testAccount = Account.builder()
                .accountId(UUID.randomUUID())
                .vpa("riya@okhdfcbank")
                .balancePaise(5000L)
                .dailyLimitPaise(100000L)
                .dailyUsedPaise(0L)
                .upiPinHash("pin-hash")
                .pinLocked(false)
                .build();

        creditRequest = CreditRequest.builder()
                .transactionId(txnId)
                .accountVpa("riya@okhdfcbank")
                .amountPaise(2500L)
                .hmacSignature("dummy-signature")
                .build();
    }

    @Test
    void processCredit_Success() {
        when(ledgerEntryRepository.findByTransactionId(txnId)).thenReturn(Collections.emptyList());
        when(accountRepository.findByVpa("riya@okhdfcbank")).thenReturn(Optional.of(testAccount));

        BankResponse response = bankService.processCredit(creditRequest);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertNotNull(response.getRrn());
        assertTrue(response.getRrn().startsWith("RRN"));

        // Verify account balance updated
        assertEquals(7500L, testAccount.getBalancePaise());
        verify(accountRepository, times(1)).save(testAccount);

        // Verify ledger entry saved
        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(1)).save(ledgerCaptor.capture());
        LedgerEntry savedLedger = ledgerCaptor.getValue();
        assertEquals(txnId, savedLedger.getTransactionId());
        assertEquals(EntryType.CREDIT, savedLedger.getType());
        assertEquals(2500L, savedLedger.getAmountPaise());
        assertEquals(testAccount, savedLedger.getAccount());
    }

    @Test
    void processCredit_Idempotency() {
        LedgerEntry existingLedger = LedgerEntry.builder()
                .entryId(UUID.randomUUID())
                .transactionId(txnId)
                .account(testAccount)
                .type(EntryType.CREDIT)
                .amountPaise(2500L)
                .rrn("RRN111111111111")
                .build();

        when(ledgerEntryRepository.findByTransactionId(txnId)).thenReturn(List.of(existingLedger));

        BankResponse response = bankService.processCredit(creditRequest);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("RRN111111111111", response.getRrn());

        // Verify no changes to account balance and no db writes
        assertEquals(5000L, testAccount.getBalancePaise());
        verify(accountRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void processCredit_AccountNotFound() {
        when(ledgerEntryRepository.findByTransactionId(txnId)).thenReturn(Collections.emptyList());
        when(accountRepository.findByVpa("riya@okhdfcbank")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> bankService.processCredit(creditRequest));

        // Verify balance not changed and no ledger written
        verify(accountRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void processReversal_Success() {
        UUID originalTxnId = UUID.randomUUID();
        UUID reversalTxnId = UUID.randomUUID();

        LedgerEntry originalDebitEntry = LedgerEntry.builder()
                .entryId(UUID.randomUUID())
                .transactionId(originalTxnId)
                .account(testAccount)
                .type(EntryType.DEBIT)
                .amountPaise(2500L)
                .rrn("RRN222222222222")
                .build();

        ReversalRequest reversalRequest = ReversalRequest.builder()
                .originalTxnId(originalTxnId)
                .reversalTxnId(reversalTxnId)
                .accountVpa("riya@okhdfcbank")
                .amountPaise(2500L)
                .build();

        testAccount.setDailyUsedPaise(2500L);

        when(ledgerEntryRepository.findByTransactionId(reversalTxnId)).thenReturn(Collections.emptyList());
        when(accountRepository.findByVpa("riya@okhdfcbank")).thenReturn(Optional.of(testAccount));
        when(ledgerEntryRepository.findByTransactionId(originalTxnId)).thenReturn(List.of(originalDebitEntry));

        BankResponse response = bankService.processReversal(reversalRequest);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertNotNull(response.getRrn());

        assertEquals(7500L, testAccount.getBalancePaise());
        assertEquals(0L, testAccount.getDailyUsedPaise());

        verify(accountRepository, times(1)).save(testAccount);

        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(1)).save(ledgerCaptor.capture());
        LedgerEntry savedLedger = ledgerCaptor.getValue();
        assertEquals(reversalTxnId, savedLedger.getTransactionId());
        assertEquals(EntryType.REVERSAL, savedLedger.getType());
        assertEquals(2500L, savedLedger.getAmountPaise());
        assertEquals(testAccount, savedLedger.getAccount());
    }

    @Test
    void processReversal_Idempotency() {
        UUID originalTxnId = UUID.randomUUID();
        UUID reversalTxnId = UUID.randomUUID();

        LedgerEntry existingReversal = LedgerEntry.builder()
                .entryId(UUID.randomUUID())
                .transactionId(reversalTxnId)
                .account(testAccount)
                .type(EntryType.REVERSAL)
                .amountPaise(2500L)
                .rrn("RRN888888888888")
                .build();

        ReversalRequest reversalRequest = ReversalRequest.builder()
                .originalTxnId(originalTxnId)
                .reversalTxnId(reversalTxnId)
                .accountVpa("riya@okhdfcbank")
                .amountPaise(2500L)
                .build();

        when(ledgerEntryRepository.findByTransactionId(reversalTxnId)).thenReturn(List.of(existingReversal));

        BankResponse response = bankService.processReversal(reversalRequest);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("RRN888888888888", response.getRrn());

        assertEquals(5000L, testAccount.getBalancePaise());
        verify(accountRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void processReversal_OriginalTxnNotFound() {
        UUID originalTxnId = UUID.randomUUID();
        UUID reversalTxnId = UUID.randomUUID();

        ReversalRequest reversalRequest = ReversalRequest.builder()
                .originalTxnId(originalTxnId)
                .reversalTxnId(reversalTxnId)
                .accountVpa("riya@okhdfcbank")
                .amountPaise(2500L)
                .build();

        when(ledgerEntryRepository.findByTransactionId(reversalTxnId)).thenReturn(Collections.emptyList());
        when(accountRepository.findByVpa("riya@okhdfcbank")).thenReturn(Optional.of(testAccount));
        when(ledgerEntryRepository.findByTransactionId(originalTxnId)).thenReturn(Collections.emptyList());

        assertThrows(TransactionNotFoundException.class, () -> bankService.processReversal(reversalRequest));

        verify(accountRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void processReversal_OriginalTxnMismatch() {
        UUID originalTxnId = UUID.randomUUID();
        UUID reversalTxnId = UUID.randomUUID();

        LedgerEntry originalDebitEntry = LedgerEntry.builder()
                .entryId(UUID.randomUUID())
                .transactionId(originalTxnId)
                .account(testAccount)
                .type(EntryType.DEBIT)
                .amountPaise(5000L)
                .rrn("RRN222222222222")
                .build();

        ReversalRequest reversalRequest = ReversalRequest.builder()
                .originalTxnId(originalTxnId)
                .reversalTxnId(reversalTxnId)
                .accountVpa("riya@okhdfcbank")
                .amountPaise(2500L)
                .build();

        when(ledgerEntryRepository.findByTransactionId(reversalTxnId)).thenReturn(Collections.emptyList());
        when(accountRepository.findByVpa("riya@okhdfcbank")).thenReturn(Optional.of(testAccount));
        when(ledgerEntryRepository.findByTransactionId(originalTxnId)).thenReturn(List.of(originalDebitEntry));

        assertThrows(TransactionNotFoundException.class, () -> bankService.processReversal(reversalRequest));

        verify(accountRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }
}
