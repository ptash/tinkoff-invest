package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllStrategy extends Factorial2TopAllStrategy {
    //public Integer getFactorialHistoryLength() { return this.getFactorialLength() * 20; }
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPrice(0.2);
        buy.setProfitPercentFromBuyMaxPrice(1.0);
        buy.setIsProfitPercentFromBuyPriceTop(false);
        buy.setIsProfitPercentFromBuyPriceTopSecond(false);
        buy.setProfitPercentFromBuyMinPriceProfit(0.0);
        buy.setProfitPercentFromBuyMaxPriceProfit(null);
        //buy.setProfitPercentFromBuyMaxPriceProfitSecond(2.0);
        buy.setProfitPercentFromBuyMaxPriceProfitSecond(null);
        buy.setAllOverProfitSecondPercent(0.0);

        buy.setIsAllOverProfit(false);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();

        sell.setTakeProfitPercent(0.2f);
        sell.setExitProfitLossPercent(0.5f);
        sell.setExitProfitInPercentMax(66.f);
        sell.setExitProfitInPercentMin(40.f);
        //sell.setExitProfitInPercentMaxForLoss(2.f);

        //sell.setStopLossLength(2);
        sell.setStopLossPercent(2.0f);

        //sell.setStopLossSoftLength(5);
        sell.setStopLossSoftPercent(1.f);

        sell.setIsExitProfitInPercentMaxMax(true);
        return sell;
    }

    public SellLimitCriteria getSellLimitCriteriaOrig() {
        //return SellLimitCriteria.builder().exitProfitPercent(4.0f).build();
        return SellLimitCriteria.builder().exitProfitPercent(4.0f).build();
    }
    public boolean isEnabled() {
        return true;
    }
}
