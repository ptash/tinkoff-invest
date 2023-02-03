package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Factorial2Strategy extends AInstrumentByFiatFactorialStrategy {
    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        FIGIES.put("BBG006L8G4H1", 1); // Yandex
        FIGIES.put("BBG005DXJS36", 5); // TCS Group (Tinkoff Bank holder)
    }

    public Integer getFactorialAvgSize() { return 8; };

    public Boolean isFactorialAvgByMiddle() { return true; };

    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }

    public boolean isEnabled() { return true; }
}
