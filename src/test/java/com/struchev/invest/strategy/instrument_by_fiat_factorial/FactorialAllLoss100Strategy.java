package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialAllLoss100Strategy extends Factorial2Strategy {

    @Override
    public Integer getFactorialHistoryLength() { return this.getFactorialLength() * 100; }
    @Override
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsAllUnderLoss(true);
        return buy;
    }
    public boolean isEnabled() { return false; }
}
