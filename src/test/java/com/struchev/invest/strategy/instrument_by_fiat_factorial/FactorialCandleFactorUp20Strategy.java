package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleFactorUp20Strategy extends FactorialCandleFactor20Strategy {

    //public SellLimitCriteria getSellLimitCriteriaOrig() {
    //    return null;
    //}
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();

        /*
        buy.setCandleOnlyUpLength(2);
        buy.setCandleOnlyUpPointLength(5);
        buy.setCandleOnlyUpBetweenPercent(0.8f);
        buy.setCandleOnlyUpBetweenPointsPercent(null);
        buy.setCandleUpSkipLength(2);
        buy.setCandleUpMinFactor(0.2f);
        buy.setCandleUpMaxFactor(2f);
        buy.setIsCandleUpAny(true);
        buy.setCandleUpMinFactorAny(0.25f);
        buy.setCandleUpMaxFactorAny(1f);
         */

        buy.setCandleDownPointSize(4);

        buy.setCandleDownPointPointLengthSize(6);
        buy.setCandleDownPointPointLength(15);
        buy.setCandleDownPointLength(30);

        buy.setCandleUpPointLength(2);
        buy.setCandleUpMinLength(0);

        buy.setCandleUpSellPointLength(2);
        buy.setCandleUpSellMinLength(1);

        //buy.setCandleDownMinMinPointLength(4);
        //buy.setCandleDownMinMinMaxLength(2);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();

        //sell.setCandleOnlyUpProfitMinPercent(0.5f);
        //sell.setCandleOnlyUpStopLossPercent(0.6f);
        //sell.setCandleExitProfitInPercentMax(null);

        sell.setIsSellUnderProfit(false);
        sell.setSellUnderLossLength(0);

        sell.setCandleUpSkipLength(4);
        sell.setCandleUpMiddleFactor(0.3f);
        sell.setCandleUpMiddleFactorMinBegin(30);

        sell.setDownAfterUpSize(2);
        return sell;
    }

    public SellLimitCriteria getSellLimitCriteriaOrig() {
        return SellLimitCriteria.builder().exitProfitPercent(5.0f).build();
    }

    public boolean isEnabled() { return false; }
}
