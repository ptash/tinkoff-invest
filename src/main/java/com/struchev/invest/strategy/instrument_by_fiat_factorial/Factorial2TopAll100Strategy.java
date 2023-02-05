package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Factorial2TopAll100Strategy extends Factorial2TopAllStrategy {
    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        //FIGIES.put("BBG00QV37ZP9", 1); // 9988 Alibaba
    }

    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 100;
    }
   // public boolean isEnabled() { return true; }
}
