package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialAllLoss200Strategy extends Factorial2Strategy {

    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 100;
    }
    public  BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsAllUnderLoss(true);
        return buy;
    }
    public boolean isEnabled() {
        return false;
    }
}
