package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllLoss20MinPriceRelativeAndPercent05Strategy extends Factorial3TopAllLoss20MinPriceRelativeStrategy {

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPriceRelativeTopMin(0.5f);
        buy.setProfitPercentFromBuyMinPriceLength(2);
        buy.setIsCurPriceMinMax(true);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setIsExitProfitInPercentMaxMax(false);
        sell.setTakeProfitPercent(0.3f);

        sell.setExitProfitInPercentMax(80.f);
        sell.setExitProfitInPercentMin(60.f);
        sell.setExitProfitInPercentMaxForLoss(.5f);
        return sell;
    }
}
