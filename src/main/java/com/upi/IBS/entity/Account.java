package com.upi.IBS.entity; 

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "account_id", updatable = false, nullable = false)
    private UUID accountId;

    @Column(name = "vpa", nullable = false, unique = true, length = 100)
    private String vpa;

    @Column(name = "balance_paise", nullable = false)
    private Long balancePaise;

    @Column(name = "daily_limit_paise", nullable = false)
    private Long dailyLimitPaise;

    @Column(name = "daily_used_paise", nullable = false)
    private Long dailyUsedPaise;

    @Column(name = "upi_pin_hash", nullable = false)
    private String upiPinHash;

    @Column(name = "pin_locked", nullable = false)
    private boolean pinLocked;

    @Column(name = "pin_attempt_count", nullable = false)
    private int pinAttemptCount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}