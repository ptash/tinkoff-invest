package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FactorialAllLoss200Strategy extends Factorial2Strategy {
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 200;
    }

    public  BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsAllUnderLoss(true);
        return buy;
    }
    //public boolean isEnabled() { return true; }
}
