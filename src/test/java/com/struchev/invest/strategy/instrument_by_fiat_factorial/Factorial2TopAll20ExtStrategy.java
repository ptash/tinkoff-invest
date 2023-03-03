package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial2TopAll20ExtStrategy extends Factorial2TopAll20Strategy {

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPrice(0.3);
        buy.setProfitPercentFromBuyMaxPrice(0.5);
        buy.setProfitPercentFromBuyMinPriceProfit(0.04);
        buy.setProfitPercentFromBuyMaxPriceProfit(-0.1);
        buy.setIsOverProfit(true);
        return buy;
    }

    public Integer getFactorialLossIgnoreSize() { return 4; };
}
