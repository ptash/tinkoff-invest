package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FactorialAllLoss100Strategy extends Factorial2Strategy {

    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        FIGIES.put("BBG222222222", 2000); // Тинькофф Золото
        FIGIES.put("BBG00KHGQ0H4", 5); // HHR HeadHunter Group PLC
        FIGIES.put("BBG004S681W1", 10); // МТС
        FIGIES.put("TCS00A103X66", 1); // POSI
        FIGIES.put("BBG002GHV6L9", 10); // SPBE
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 100;
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
