package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {
    public Boolean isTubeAvgDeltaAdvance() { return true; }

    /*
    public Integer getAvgLength() {
        return getSmaSlowestLength();
    }

    public Integer getAvgTubeLength() {
        return getSmaTubeLength() * 2;
    }
     */

    @Override
    public SellCriteria getSellCriteria() {
        return SellCriteria.builder().takeProfitPercent(1f).stopLossPercent(2f).build();
    }

    public boolean isEnabled() { return true; }
}
