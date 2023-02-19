package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllMinLossStrategy extends Factorial3TopAllStrategy {
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsProfitPercentFromBuyPriceTopSecond(true);
        buy.setProfitPercentFromBuyMaxPriceProfitSecond(2.0);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setExitProfitInPercentMaxForLoss(2.f);
        return sell;
    }
}
