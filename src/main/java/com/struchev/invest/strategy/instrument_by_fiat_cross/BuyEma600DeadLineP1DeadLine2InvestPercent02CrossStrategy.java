package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyEma600DeadLineP1DeadLine2InvestPercent02CrossStrategy extends ABuyEma600CrossStrategy {

    private Map FIGIES = Map.of(
            "BBG004730RP0", 10, // Газпром
            "BBG004S683W7", 10,   // Аэрофлот
            "BBG004S68696", 10,    // Распадская
            "BBG00178PGX3", 1,    // VK
            "BBG00475KKY8", 1, // НОВАТЭК
            "BBG000LWVHN8", 2000 // Дагестанская энергосбытовая компания

            //"BBG006G2JVL2", 1, // Alibaba
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }

    public Double getDeadLinePercent() { return 1.0; }

    public Double getDeadLinePercentFromSmaSlowest() { return 2.0; }

    public Double getInvestPercentFromFast() { return 0.2; }

    @Override
    public AInstrumentByFiatCrossStrategy.SellCriteria getSellCriteria() {
        return AInstrumentByFiatCrossStrategy.SellCriteria.builder().takeProfitPercent(1f).stopLossPercent(2f).build();
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
