package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossTube2Strategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy {
    private Map FIGIES = Map.of(
            "BBG004S681W1", 20,    // МТС
            "BBG00178PGX3", 12    // VK
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }
    public Boolean isTubeAvgDeltaAdvance2() { return true; }

    public Integer getAvgLength() {return getSmaFastLength() / 2;}

    public boolean isEnabled() { return true; }
    public boolean isArchive() {return false;}
}
