package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import com.struchev.invest.strategy.instrument_by_fiat_cross.AInstrumentByFiatCrossStrategy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FactorialStrategy extends AInstrumentByFiatFactorialStrategy {
    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        //FIGIES.put("BBG000LWVHN8", 1); // Дагестанская энергосбытовая компания
        //FIGIES.put("BBG004S681W1", 10); // МТС
        //FIGIES.put("BBG00475KKY8", 1); // НОВАТЭК
        //FIGIES.put("BBG006L8G4H1", 1); // Yandex
        //FIGIES.put("BBG00178PGX3", 1); // VK
        //FIGIES.put("BBG00QPYJ5H0", 1); // TCS Group
        //FIGIES.put("BBG004730JJ5", 1); // Московская Биржа
        //FIGIES.put("BBG002GHV6L9", 1); // SPBE
        //FIGIES.put("TCS00A103X66", 1); // POSI
        FIGIES.put("BBG004731032", 1); // ЛУКОЙЛ
        //FIGIES.put("BBG002458LF8", 1); // SELG Селигдар
        //FIGIES.put("BBG004730RP0", 1); // Газпром
        //FIGIES.put("BBG222222222", 100); // Тинькофф Золото
        //FIGIES.put("BBG005DXJS36", 1); // TCS Group (Tinkoff Bank holder)
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }

    public boolean isEnabled() {
        return true;
    }
}
