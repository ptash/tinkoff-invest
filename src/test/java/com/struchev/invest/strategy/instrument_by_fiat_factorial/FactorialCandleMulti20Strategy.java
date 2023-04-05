package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleMulti20Strategy extends FactorialCandle20Strategy {

    public SellLimitCriteria getSellLimitCriteriaOrig() {
        return null;
    }
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        return sell;
    }
}
