package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleFactor20Strategy extends FactorialCandle20Strategy {

    //public SellLimitCriteria getSellLimitCriteriaOrig() {
    //    return null;
    //}
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setCandlePriceMinFactor(0.5f);
        buy.setCandlePriceMaxFactor(2.5f);
        buy.setCandlePriceMinMinFactor(0.05f);
        buy.setCandlePriceMinMaxFactor(0.15f);
        buy.setCandleMinFactor(0.5f);
        buy.setCandleMaxFactor(10f);
        buy.setCandleProfitMinPercent(0.15f);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setCandleProfitMinPercent(0.12f);
        sell.setCandlePriceMinFactor(0.5f);
        //sell.setSellUnderLossLength(2);
        return sell;
    }
}
