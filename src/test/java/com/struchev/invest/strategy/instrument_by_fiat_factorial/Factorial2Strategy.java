package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Factorial2Strategy extends AInstrumentByFiatFactorialStrategy {
    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        //FIGIES.put("BBG000LWVHN8", 1); // Дагестанская энергосбытовая компания
        //FIGIES.put("BBG004S681W1", 10); // МТС
        //FIGIES.put("BBG00475KKY8", 1); // НОВАТЭК
        //FIGIES.put("BBG006L8G4H1", 1); // Yandex
        //FIGIES.put("BBG00178PGX3", 1); // VK
        FIGIES.put("BBG00QPYJ5H0", 1); // TCS Group
        //FIGIES.put("BBG004730JJ5", 1); // Московская Биржа
        FIGIES.put("BBG005DXJS36", 1); // TCS Group (Tinkoff Bank holder)
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }

    public Integer getFactorialAvgSize() { return 4; };

    public Boolean isFactorialAvgByMiddle() { return true; };

    public AInstrumentByFiatFactorialStrategy.SellCriteria getSellCriteria() {
        var sell = super.getSellCriteria();
        sell.setIsSellUnderProfit(true);
        return sell;
    }
    public boolean isEnabled() {
        return true;
    }
}
