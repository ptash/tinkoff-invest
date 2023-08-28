package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandle20Strategy extends Factorial2Strategy {
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 20;
    }

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsOverProfit(false);
        buy.setIsAllOverProfit(false);
        buy.setIsAllUnderLoss(false);
        buy.setTakeProfitLossPercent(null);


        buy.setCandleMaxIntervalLess(2400);
        buy.setCandleMaxInterval(180);
        buy.setCandleIntervalMinPercent(1.f);
        buy.setCandleMinLength(6);
        buy.setCandleMaxLength(12);
        buy.setCandleUpMiddleLength(1);
        buy.setCandleUpLength(2);

        buy.setCandleUpDownSkipCount(1);
        buy.setCandleUpDownSkipLength(15);
        buy.setCandleUpDownSkipDeviationPercent(40f);

        //buy.setProfitPercentFromBuyMinPrice(-0.01);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setCandleMaxIntervalLess(2400);
        sell.setCandleMaxInterval(180);
        sell.setCandleIntervalMinPercent(0.5f);
        sell.setCandleMinLength(6);
        sell.setCandleMaxLength(12);
        sell.setCandleUpMiddleLength(1);
        sell.setCandleUpLength(2);

        sell.setExitLossPercent(2f);

        sell.setTakeProfitPercent(null);
        sell.setExitProfitInPercentMax(null);
        sell.setExitLossPercent(null);
        sell.setStopLossPercent(null);
        sell.setStopLossSoftPercent(null);

        sell.setIsSellUnderProfit(true);
        sell.setSellUnderLossLength(2);

        sell.setProfitPercentFromSellMinPrice(0.01f);
        sell.setProfitPercentFromSellMaxPrice(0.05f);
        sell.setProfitPercentFromSellMinPriceLength(10);
        return sell;
    }

    public Integer getPriceDiffAvgLength() { return 4; }
    public Float getPriceDiffAvg() { return 3f; }

    public boolean isEnabled() { return false; }
}
