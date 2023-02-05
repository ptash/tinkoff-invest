package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FactorialAllLoss50Strategy extends FactorialAllLoss200Strategy {

    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        FIGIES.put("BBG000BFNTD0", 100); // 857 PetroChina
        //FIGIES.put("BBG00KVTBY91", 10); // 1810 Xiaomi
    }
    //public Map<String, Integer> getFigies()  { return FIGIES; }
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 50;
    }

    //public boolean isEnabled() { return true; }
}
