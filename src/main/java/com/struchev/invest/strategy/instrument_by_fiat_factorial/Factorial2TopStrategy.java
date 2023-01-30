package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Factorial2TopStrategy extends Factorial2Strategy {

    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        FIGIES.put("BBG0029SG1C1", 100); // KZOSP ПАО «КАЗАНЬОРГСИНТЕЗ» - акции привилегированные
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }
    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setProfitPercentFromBuyMinPrice(0.1);
        buy.setIsOverProfit(true);
        return buy;
    }
    public boolean isEnabled() {
        return true;
    }
}
