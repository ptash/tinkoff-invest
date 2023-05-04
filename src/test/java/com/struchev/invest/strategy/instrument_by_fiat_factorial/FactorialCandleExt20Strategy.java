package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleExt20Strategy extends FactorialCandle20Strategy {
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPriceLength(2);
        buy.setProfitPercentFromBuyMinPrice(-0.5);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        //sell.setCandleIntervalMinPercent(0.5f);
        sell.setExitLossPercent(1f);
        sell.setProfitPercentFromSellMinPriceLength(2);
        return sell;
    }

    public boolean isEnabled() { return false; }
}
