package com.struchev.invest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderReportInstrumentByFiatRow {
    private String figi;
    private String figiTitle;
    private String strategy;
    private BigDecimal firstPrice;
    private BigDecimal lastPrice;
    private BigDecimal profitByRobot;
    private BigDecimal profitByInvest;
    private Integer orders;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
}
