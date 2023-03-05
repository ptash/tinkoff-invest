package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Factorial2TopAllStrategy extends Factorial2TopStrategy {
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsAllUnderLoss(true);
        buy.setIsAllOverProfit(true);
        return buy;
    }
    //public boolean isEnabled() { return false; }
}
