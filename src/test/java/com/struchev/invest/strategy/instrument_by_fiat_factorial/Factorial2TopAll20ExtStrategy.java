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
        buy.setIsAllUnderLoss(true);
        buy.setIsAllOverProfit(true);
        buy.setIsOverProfitWaitFirstUnderProfit(true);
        buy.setOverProfitWaitFirstUnderProfitPercent(0.1);
        buy.setOverProfitSkipWaitFirstOverProfitPercent(0.5);

        buy.setUnderLostWaitCandleEndInMinutes(5);
        return buy;
    }

    /*
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
    }*/

    public Integer getFactorialLossIgnoreSize() { return 4; };
}
