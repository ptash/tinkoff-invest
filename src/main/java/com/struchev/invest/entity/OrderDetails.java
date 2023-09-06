package com.struchev.invest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    public Map<String, BigDecimal> getCurrentPrices() {
        if (currentPrices != null) {
            return currentPrices;
        }
        return new HashMap<>();
    }

    public Map<String, Boolean> getBooleanDataMap() {
        if (booleanDataMap != null) {
            return booleanDataMap;
        }
        return new HashMap<>();
    }
}