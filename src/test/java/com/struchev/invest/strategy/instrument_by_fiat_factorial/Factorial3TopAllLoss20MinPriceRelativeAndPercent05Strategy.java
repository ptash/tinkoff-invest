package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllLoss20MinPriceRelativeAndPercent05Strategy extends Factorial3TopAllLoss20Strategy {

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setOverProfitSkipIfSellPrev(0);
        /*buy.setProfitPercentFromBuyMinPriceRelativeTopMin(0.5f);
        buy.setProfitPercentFromBuyMinPriceLength(2);
        buy.setIsCurPriceMinMax(true);

        buy.setProfitPercentFromBuyMinPrice(null);
        buy.setProfitPercentFromBuyMinPriceRelativeMin(null);*/
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setIsExitProfitInPercentMaxMax(false);
        sell.setTakeProfitPercent(0.3f);

        sell.setExitProfitInPercentMax(90.f);
        sell.setExitProfitInPercentMin(30.f);
        sell.setExitProfitInPercentMaxForLoss(.5f);
        sell.setTakeProfitPercentForLoss(0.1f);
        sell.setExitProfitInPercentMaxForLoss2(100.f);
        sell.setExitProfitInPercentMaxLoopIgnoreSize(1);
        return sell;
    }

    public SellLimitCriteria getSellLimitCriteria() {
        //return SellLimitCriteria.builder().exitProfitPercent(4.0f).build();
        return SellLimitCriteria.builder().exitProfitPercent(4.0f).build();
    }
    public Integer getFactorialLossIgnoreSize() { return 4; };
}
