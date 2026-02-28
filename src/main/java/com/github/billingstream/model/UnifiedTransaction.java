package com.github.billingstream.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedTransaction {
    private String transactionId;
    private String userId;
    private double amount;
    private String status;
    private String paymentMethod;
    private String paymentReference;
    private String paymentDetail;
    private Instant timestamp;
}
