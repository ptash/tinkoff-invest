package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleFactorOnlyUp20Strategy extends FactorialCandleFactorUp20Strategy {

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsOnlyUp(true);
        return buy;
    }

    public SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setIsOnlyStopLoss(true);
        return sell;
    }

    public SellLimitCriteria getSellLimitCriteriaOrig() {
        return null;
        //return SellLimitCriteria.builder().exitProfitPercent(15.0f).build();
    }

    public boolean isEnabled() { return true; }
}
