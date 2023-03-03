package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllAllLoss20MinPriceRelativeAndPercent05Strategy extends Factorial3TopAllLoss20MinPriceRelativeAndPercent05Strategy {
    public  BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsAllUnderLoss(true);
        buy.setIsAllOverProfit(true);
        return buy;
    }
}
