package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleFactorOnlyUp20FiveMinStrategy extends FactorialCandleFactorOnlyUp20Strategy {
    public String getInterval() {
        return "5min";
    }

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setCandleInterval("5min");
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setCandleInterval("5min");
        return sell;
    }
    //public boolean isEnabled() { return true; }
}
