package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllStrategy extends Factorial2TopAllStrategy {
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPrice(0.1);
        buy.setProfitPercentFromBuyMaxPrice(0.2);
        buy.setIsProfitPercentFromBuyPriceTop(false);
        buy.setIsProfitPercentFromBuyPriceTopSecond(true);
        buy.setAllOverProfitSecondPercent(0.0);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();

        sell.setTakeProfitPercent(1.f);
        sell.setExitProfitLossPercent(0.5f);
        sell.setExitProfitInPercentMax(66.f);
        sell.setExitProfitInPercentMin(40.f);

        //sell.setStopLossLength(2);
        sell.setStopLossPercent(2.0f);

        //sell.setStopLossSoftLength(5);
        sell.setStopLossSoftPercent(1.f);
        return sell;
    }

    public SellLimitCriteria getSellLimitCriteria() {
        //return SellLimitCriteria.builder().exitProfitPercent(4.0f).build();
        return SellLimitCriteria.builder().exitProfitPercent(15.0f).build();
    }
    public boolean isEnabled() {
        return true;
    }
}
