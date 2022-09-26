package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {
    private Map FIGIES = Map.of(
            //"BBG002NLDLV8", 4 // VIPS
            //"BBG00178PGX3", 12    // VK
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }
    public Boolean isTubeAvgDeltaAdvance() { return true; }

    public boolean isEnabled() { return false; }

    public boolean isArchive() {return true;}
}
