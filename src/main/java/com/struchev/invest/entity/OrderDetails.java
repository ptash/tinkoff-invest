package com.struchev.invest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetails {
    Map<String, BigDecimal> currentPrices;
    Map<String, Boolean> booleanDataMap;
    Map<String, OffsetDateTime> dateTimes;
    Map<String, String> annotations;
    Map<String, Integer> currentInts;

    BigDecimal priceWanted;
    BigDecimal limitPercent;
    BigDecimal limitPrice;
    Boolean isReverse;

    public Map<String, Integer> getCurrentInts() {
        if (currentInts == null) {
            currentInts = new HashMap<>();
        }
        return currentInts;
    }

    public Map<String, BigDecimal> getCurrentPrices() {
        if (currentPrices == null) {
            currentPrices = new HashMap<>();
        }
        return currentPrices;
    }

    public Map<String, Boolean> getBooleanDataMap() {
        if (booleanDataMap == null) {
            booleanDataMap = new HashMap<>();
        }
        return booleanDataMap;
    }

    public Map<String, OffsetDateTime> getDateTimes() {
        if (dateTimes == null) {
            dateTimes = new HashMap<>();
        }
        return dateTimes;
    }

    public Map<String, String> getAnnotations() {
        if (annotations == null) {
            annotations = new HashMap<>();
        }
        return annotations;
    }

    public BigDecimal getPriceWanted() {
        return getCurrentPrices().getOrDefault("priceWanted", null);
    }
    public BigDecimal getLimitPercent() {
        return getCurrentPrices().getOrDefault("limitPercent", null);
    }
    public BigDecimal getLimitPrice() {
        return getCurrentPrices().getOrDefault("limitPrice", null);
    }

    public Boolean getIsReverse() {
        return getBooleanDataMap().getOrDefault("isReverse", false);
    }
}