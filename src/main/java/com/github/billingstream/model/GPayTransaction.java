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
public class GPayTransaction {
    private String transactionId;
    private String userId;
    private double amount;
    private String status;
    private String upiId;
    private String deviceId;
    private String gpayReferenceId;
    private Instant timestamp;
}
