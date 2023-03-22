package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial50ExtRiskStrategy extends Factorial2TopAll20ExtStrategy {

    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 50;
    }

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setSkipIfOutPrevLength(null);
        buy.setOverProfitSkipIfOverProfitLength(null);
        return buy;
    }
}
