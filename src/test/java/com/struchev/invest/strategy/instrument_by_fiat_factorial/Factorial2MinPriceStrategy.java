package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial2MinPriceStrategy extends Factorial2Strategy {
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPrice(0.1);
        buy.setProfitPercentFromBuyMaxPrice(0.3);
        return buy;
    }
    public boolean isEnabled() {
        return true;
    }
}
