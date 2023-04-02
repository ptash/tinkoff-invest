package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleExt20Strategy extends FactorialCandle20Strategy {
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setCandleIntervalMinPercent(0.5f);
        return sell;
    }
}
