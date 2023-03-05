package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial2TopMinPriceNullStrategy extends Factorial2TopStrategy {
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPrice(null);
        buy.setIsOverProfit(true);
        return buy;
    }
    public boolean isEnabled() {
        return false;
    }
}
