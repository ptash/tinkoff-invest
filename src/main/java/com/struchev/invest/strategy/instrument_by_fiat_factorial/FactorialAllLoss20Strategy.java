package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FactorialAllLoss20Strategy extends FactorialAllLoss200Strategy {

    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        //FIGIES.put("BBG00ZNW65W3", 1); // 9626 Bilibili
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 20;
    }

    public boolean isEnabled() {
        return false;
        //return true;
    }
}
