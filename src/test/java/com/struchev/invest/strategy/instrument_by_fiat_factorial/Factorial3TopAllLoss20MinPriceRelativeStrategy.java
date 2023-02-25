package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllLoss20MinPriceRelativeStrategy extends Factorial3TopAllLoss20MinPriceStrategy {

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPrice(null);
        buy.setProfitPercentFromBuyMinPriceRelativeTop(33.f);
        buy.setProfitPercentFromBuyMinPriceRelativeMin(10.f);
        buy.setProfitPercentFromBuyMinPriceRelativeMax(30.f);
        //buy.setIsProfitPercentFromBuyMinPriceRelativeMaxMax(true);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setIsExitProfitInPercentMaxMax(false);
        return sell;
    }
    //public boolean isEnabled() { return false; }
}
