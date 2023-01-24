package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FactorialAllLossStrategy extends Factorial2Strategy {

    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        FIGIES.put("TCS00A103X66", 1); // POSI
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }
    public  BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsAllUnderLoss(true);
        return buy;
    }
    public boolean isEnabled() {
        return true;
    }
}
