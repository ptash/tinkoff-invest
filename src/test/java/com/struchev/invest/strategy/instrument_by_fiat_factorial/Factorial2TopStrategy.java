package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial2TopStrategy extends Factorial2Strategy {

    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 200;
    }
    public Integer getFactorialLengthFuture() { return 8; }

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsOverProfit(true);
        return buy;
    }
    public boolean isEnabled() {
        return true;
    }
}
