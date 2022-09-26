package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossNotCellStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {

    private Map FIGIES = Map.of(
            "BBG000QGWY50", 6 // Bluebird Bio Inc
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }
    public Boolean isNotCellIfBuy() { return true; }

    public boolean isEnabled() { return true; }
    public boolean isArchive() {return false;}
}
