package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AlligatorStrategy extends AAlligatorStrategy {
    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        //FIGIES.put("FUTNG1124000", 1); // NG-11.24 Природный газ
        FIGIES.put("FUTNG0125000", 1); // NG-01.25 Природный газ
        FIGIES.put("FUTGLDRUBF00", 1); // GLDRUBF Золото (rub)
        FIGIES.put("FUTUSDRUBF00", 1); // USDRUBF USDRUBF Доллар - Рубль
        FIGIES.put("FUTIMOEXF000", 1); // USDRUBF USDRUBF Доллар - Рубль
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }

    public boolean isEnabled() { return false; }
}
