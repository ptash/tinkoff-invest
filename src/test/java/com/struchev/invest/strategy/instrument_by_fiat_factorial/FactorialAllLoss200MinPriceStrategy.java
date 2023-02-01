package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialAllLoss200MinPriceStrategy extends FactorialAllLoss200Strategy {

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPrice(0.1);
        buy.setProfitPercentFromBuyMaxPrice(1.0);
        return buy;
    }
    public boolean isEnabled() {
        return true;
    }
}
