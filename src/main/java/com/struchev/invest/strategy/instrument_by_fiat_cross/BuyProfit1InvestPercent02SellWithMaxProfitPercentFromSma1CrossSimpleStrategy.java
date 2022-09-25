package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitPercentFromSma1CrossSimpleStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitPercentFromSma1CrossStrategy {

    private Map FIGIES = Map.of(
    );
    public Map<String, Integer> getFigies() {
        return FIGIES;
    }
    public Boolean isTubeAvgDeltaSimple() { return true; }
    public boolean isEnabled() { return false; }
}
