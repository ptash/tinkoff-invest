package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Factorial2Top20Strategy extends Factorial2TopStrategy {

    private static final Map FIGIES = new HashMap<String, Integer>();

    public Integer getFactorialHistoryLength() { return this.getFactorialLength() * 20; }
    //public boolean isEnabled() { return true; }
}
