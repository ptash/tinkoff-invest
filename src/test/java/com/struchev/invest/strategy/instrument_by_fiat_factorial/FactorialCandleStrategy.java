package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleStrategy extends FactorialStrategy {
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsOverProfit(false);
        buy.setIsAllOverProfit(false);
        buy.setIsAllUnderLoss(false);
        buy.setTakeProfitLossPercent(null);

        buy.setCandleIntervalMinPercent(1.f);
        buy.setCandleMinLength(6);
        buy.setCandleUpMiddleLength(1);
        buy.setCandleUpLength(2);
        return buy;
    }

    public Integer getPriceDiffAvgLength() { return 4; }
    public Float getPriceDiffAvg() { return 3f; }

    public boolean isEnabled() {
        return true;
    }
}
