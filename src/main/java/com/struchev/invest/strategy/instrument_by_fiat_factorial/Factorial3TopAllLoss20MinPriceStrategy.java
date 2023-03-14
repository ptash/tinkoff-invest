package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllLoss20MinPriceStrategy extends Factorial3TopAllLoss20Strategy {

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPrice(0.5);
        buy.setProfitPercentFromBuyMaxPrice(0.5);
        return buy;
    }
}
