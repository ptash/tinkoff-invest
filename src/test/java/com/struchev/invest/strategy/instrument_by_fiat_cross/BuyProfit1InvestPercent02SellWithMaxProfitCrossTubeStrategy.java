package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.time.Duration;

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

    //public Double getPercentMoveUpError() { return 0.05; }

    public boolean isEnabled() { return true; }
}
