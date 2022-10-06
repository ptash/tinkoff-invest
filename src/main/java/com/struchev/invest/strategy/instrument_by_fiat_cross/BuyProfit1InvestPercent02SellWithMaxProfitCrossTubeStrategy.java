package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {
    private Map FIGIES = Map.of(
            "BBG004S68CP5", 5   // М.видео
/*
            "BBG005F1DK91", 2, // G1
            "BBG002NLDLV8", 4 // VIPS
            //"BBG00178PGX3", 12    // VK*/
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }
    public Boolean isTubeAvgDeltaAdvance() { return true; }

    public SellLimitCriteria getSellLimitCriteria() {
        return SellLimitCriteria.builder().exitProfitPercent(1f).build();
    }

    @Override
    public SellCriteria getSellCriteria() {
        return SellCriteria.builder().takeProfitPercent(0.5f).stopLossPercent(1f).build();
    }

    public Duration getDelayBySL() {
        return null;
    }
    public Integer getDelayPlusBySL() { return 30; }

    public boolean isEnabled() { return true; }

    public boolean isArchive() {return false;}
}
