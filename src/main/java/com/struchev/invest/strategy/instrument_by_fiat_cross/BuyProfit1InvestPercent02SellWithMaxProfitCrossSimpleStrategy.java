package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossSimpleStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {

    private Map FIGIES = Map.of(
            "BBG000LWVHN8", 60000 // Дагестанская энергосбытовая компания
    );
    public Map<String, Integer> getFigies() {
        return FIGIES;
    }

    public Boolean isTubeAvgDeltaSimple() { return true; }

    @Override
    public boolean isEnabled() { return true; }

    public boolean isArchive() { return false; }
}
