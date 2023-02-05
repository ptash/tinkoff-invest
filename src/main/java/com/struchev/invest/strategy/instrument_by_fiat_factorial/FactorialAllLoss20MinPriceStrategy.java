package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FactorialAllLoss20MinPriceStrategy extends FactorialAllLoss200Strategy {

    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        //FIGIES.put("BBG0120WC125", 1); // 2015 Li Auto
    }
    //public Map<String, Integer> getFigies()  { return FIGIES; }
    public Integer getFactorialHistoryLength() { return this.getFactorialLength() * 20; }
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPrice(0.1);
        buy.setProfitPercentFromBuyMaxPrice(1.0);
        return buy;
    }
    //public boolean isEnabled() { return true; }
}
