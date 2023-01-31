package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Factorial2TopAllStrategy extends Factorial2TopStrategy {
    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        FIGIES.put("BBG00QPYJ5H0", 1); // TCS Group
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsAllUnderLoss(true);
        buy.setIsAllOverProfit(true);
        return buy;
    }
    public boolean isEnabled() {
        return false;
    }
}
