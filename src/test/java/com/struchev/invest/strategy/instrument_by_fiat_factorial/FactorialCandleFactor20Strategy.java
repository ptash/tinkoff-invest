package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleFactor20Strategy extends FactorialCandle20Strategy {

    //public SellLimitCriteria getSellLimitCriteriaOrig() {
    //    return null;
    //}
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setCandlePriceMinFactor(0.65f);
        buy.setCandlePriceMaxFactor(2.5f);
        buy.setCandlePriceMinMinFactor(0.05f);
        buy.setCandlePriceMinMaxFactor(0.15f);
        buy.setCandleMinFactor(0.5f);
        buy.setCandleMaxFactor(10f);
        buy.setCandleMinFactorCandle(0.3f);
        buy.setCandleProfitMinPercent(0.15f);
        buy.setEmaLength(200);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setCandleProfitMinPercent(0.15f);
        sell.setCandleUpSkipDownBetweenFactor(0.2f);
        sell.setCandlePriceMinFactor(0.5f);
        sell.setCandleTrySimple(2);
        //sell.setSellUnderLossLength(2);

        sell.setCandleUpSkipLength(2);
        sell.setCandleUpPointLength(5);
        sell.setCandleUpMaxLength(4);
        return sell;
    }

    public boolean isEnabled() { return true; }
}
