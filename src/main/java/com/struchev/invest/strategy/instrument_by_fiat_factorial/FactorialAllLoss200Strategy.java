package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FactorialAllLoss200Strategy extends Factorial2Strategy {

    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        FIGIES.put("BBG00178PGX3", 1); // VK
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 200;
    }

    public  BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsAllUnderLoss(true);
        return buy;
    }
    public boolean isEnabled() {
        return true;
    }
}
