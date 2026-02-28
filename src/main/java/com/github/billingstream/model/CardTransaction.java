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
public class CardTransaction {
    private String transactionId;
    private String userId;
    private double amount;
    private String status;
    private String cardNumber;
    private String cardHolderName;
    private String cardNetwork;
    private String expiryDate;
    private Instant timestamp;
}
