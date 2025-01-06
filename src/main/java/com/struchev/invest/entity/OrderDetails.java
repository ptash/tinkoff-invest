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

    BigDecimal priceWanted;

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
}