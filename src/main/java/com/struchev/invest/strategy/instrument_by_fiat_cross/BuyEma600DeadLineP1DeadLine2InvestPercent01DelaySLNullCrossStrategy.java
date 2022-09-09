package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class BuyEma600DeadLineP1DeadLine2InvestPercent01DelaySLNullCrossStrategy extends BuyEma600DeadLineP1DeadLine2InvestPercent02CrossStrategy {

    private Map FIGIES = Map.of(
            //"BBG006G2JVL2", 1, // Alibaba
            "BBG000QGWY50", 3, // Bluebird Bio Inc
            "BBG000BR85F1", 1, // PetroChina
            //"BBG004NLQHL0", 1, // Fastly Inc
            "BBG000BPWXK1", 1 // Newmont Goldcorp Corporation
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }
    public Double getInvestPercentFromFast() { return 0.1; }

    public Duration getDelayBySL() {
        return null;
    }

    public boolean isEnabled() {
        return true;
    }
}
