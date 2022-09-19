package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class BuyEma600DeadLineP1DeadLine2InvestPercent02DelaySLNullCrossStrategy extends BuyEma600DeadLineP1DeadLine2InvestPercent02CrossStrategy {
    private Map FIGIES = Map.of(
            "BBG000LNHHJ9", 10, // КАМАЗ
            "BBG004730RP0", 10, // Газпром
            //"BBG004S683W7", 9,   // Аэрофлот move to BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02CrossStrategy
            //"BBG004S68696", 1,    // Распадская move to BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02CrossStrategy
            "BBG00178PGX3", 6,    // VK
            //"BBG00475KKY8", 2, // НОВАТЭК move to BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02CrossStrategy
            "BBG000LWVHN8", 6000 // Дагестанская энергосбытовая компания

            //"BBG006G2JVL2", 1 // Alibaba
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }
    public Duration getDelayBySL() {
        return null;
    }

    public boolean isEnabled() { return true; }

    public boolean isArchive() { return true; }
}
