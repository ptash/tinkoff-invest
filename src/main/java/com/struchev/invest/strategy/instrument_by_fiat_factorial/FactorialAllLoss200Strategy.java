package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FactorialAllLoss200Strategy extends Factorial2Strategy {

    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        FIGIES.put("BBG002W2FT69", 10); // АбрауДюрсо
        FIGIES.put("BBG000NLB2G3", 10); // KROT Красный Октябрь
        FIGIES.put("BBG004730RP0", 10); // Газпром
        FIGIES.put("BBG004731032", 1); // LKOH ЛУКОЙЛ
        FIGIES.put("BBG00Y91R9T3", 1); // OZON
        //FIGIES.put("BBG0029SG1C1", 100); // KZOSP ПАО «КАЗАНЬОРГСИНТЕЗ» - акции привилегированные
        FIGIES.put("BBG002458LF8", 50); // SELG Селигдар
        FIGIES.put("BBG00178PGX3", 5); // VK
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 200;
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
