package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllLoss20MinPriceRelativeAndPercent05Strategy extends Factorial3TopAllLoss20MinPriceRelativeStrategy {

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPriceRelativeTopMin(0.5f);
        buy.setProfitPercentFromBuyMinPriceLength(2);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setIsExitProfitInPercentMaxMax(true);
        sell.setTakeProfitPercent(0.3f);
        return sell;
    }
}
