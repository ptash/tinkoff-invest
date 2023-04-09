package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleFactorUp20Strategy extends FactorialCandleFactor20Strategy {

    //public SellLimitCriteria getSellLimitCriteriaOrig() {
    //    return null;
    //}
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();

        buy.setCandleOnlyUpLength(2);
        buy.setCandleOnlyUpPointLength(5);
        buy.setCandleOnlyUpBetweenPercent(0.8f);
        buy.setCandleOnlyUpBetweenPointsPercent(null);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();

        sell.setCandleOnlyUpProfitMinPercent(0.5f);
        sell.setCandleOnlyUpStopLossPercent(0.6f);
        sell.setCandleExitProfitInPercentMax(null);
        return sell;
    }
}
