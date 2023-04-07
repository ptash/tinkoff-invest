package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleMulti20Strategy extends FactorialCandle20Strategy {

    //public SellLimitCriteria getSellLimitCriteriaOrig() {
    //    return null;
    //}
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setCandlePriceMinFactor(0.5f);
        buy.setCandleMinFactor(0.5f);
        buy.setCandleMaxFactor(10f);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        return sell;
    }
}
