package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyEma600DeadLineP1DeadLine2InvestPercent02CrossStrategy extends ABuyEma600CrossStrategy {

    private Map FIGIES = Map.of();

    //public Map<String, Integer> getFigies() { return FIGIES; }

    public Double getDeadLinePercent() { return 1.0; }

    public Double getDeadLinePercentFromSmaSlowest() { return 2.0; }

    public Double getInvestPercentFromFast() { return 0.2; }

    @Override
    public AInstrumentByFiatCrossStrategy.SellCriteria getSellCriteria() {
        return AInstrumentByFiatCrossStrategy.SellCriteria.builder().takeProfitPercent(1f).stopLossPercent(2f).build();
    }

    public SellLimitCriteria getSellLimitCriteria() {
        return SellLimitCriteria.builder().exitProfitPercent(1.5f).build();
    }

    //public boolean isEnabled() { return false; }
}
