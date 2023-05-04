package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllLoss200Strategy extends Factorial3TopAllStrategy {

    public Integer getFactorialHistoryLength() { return this.getFactorialLength() * 200; }
    public  BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPrice(null);
        return buy;
    }
    public boolean isEnabled() { return false; }
}
