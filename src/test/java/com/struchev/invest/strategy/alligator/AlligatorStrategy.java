package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AlligatorStrategy extends AAlligatorStrategy {
    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        //FIGIES.put("FUTNG1224000", 1); // NG-12.24 Природный газ
        //FIGIES.put("FUTNG0125000", 1); // NG-01.25 Природный газ
        //FIGIES.put("FUTGLDRUBF00", 1); // GLDRUBF Золото (rub)
        //FIGIES.put("FUTUSDRUBF00", 1); // USDRUBF USDRUBF Доллар - Рубль
        //FIGIES.put("FUTIMOEXF000", 1);
        //FIGIES.put("FUTBR0125000", 1);
        //FIGIES.put("FUTGOLD01250", 1);
        //FIGIES.put("FUTMIX032500", 1); //MIX-12.24 Индекс МосБиржи

        //FIGIES.put("FUTNG0625000", 1); // NG-06.25 Природный газ
        //FIGIES.put("FUTBR0725000", 1);
        //FIGIES.put("FUTGOLD09250", 1);
        //FIGIES.put("FUTIBIT09250", 1);
        //FIGIES.put("FUTMIX092500", 1); //MIX-9.25 Индекс МосБиржи

        //FIGIES.put("FUTCOCOA0925", 1); //COCOA-9.25 Какао
        //FIGIES.put("FUTCOFFE1125", 1); //COFFEE-11.25 Кофе
        //FIGIES.put("FUTGAZR09250", 1); //GAZR-9.25 Газпром
        //FIGIES.put("FUTSPBE09250", 1); //SPBE-9.25 СПБ Биржа
        //FIGIES.put("FUTSBERF0000", 1); //SBERF Сбер Банк (обыкновенные)
        //FIGIES.put("FUTETHA09250", 1); //ETHA-9.25 Ethereum ETF

        //FIGIES.put("FUTNG0725000", 1);
        //FIGIES.put("FUTBR0825000", 1);

        //FIGIES.put("FUTNG0825000", 1);
        //FIGIES.put("FUTBR0925000", 1);

        //FIGIES.put("FUTNG0925000", 1);


        //FIGIES.put("FUTNG1225000", 1);

        //2025-12-13 21d

        FIGIES.put("FUTNG1225000", 1);
        FIGIES.put("FUTCOCOA1225", 1); //COCOA-9.25 Какао
        FIGIES.put("FUTMIX122500", 1);
        FIGIES.put("FUTCOFFE0226", 1); //COFFEE-11.25 Кофе
        FIGIES.put("FUTIMOEXF000", 1);
        FIGIES.put("FUTGOLD12250", 1);
        FIGIES.put("FUTSILV12250", 1); // SVU3 SILV-9.23 Серебро

/*
        FIGIES.put("FUTIBIT12250", 1);
        FIGIES.put("FUTGAZR12250", 1);
        c
        FIGIES.put("FUTUSDRUBF00", 1); // USDRUBF USDRUBF Доллар - Рубль

*/
        //FIGIES.put("FUTBR0126000", 1);

        // 2025-12-27 14d
/*
        FIGIES.put("FUTNG1225000", 1);
        FIGIES.put("FUTMIX032600", 1);
        FIGIES.put("FUTGOLD03260", 1);
        FIGIES.put("FUTCOFFE0226", 1); //COFFEE-11.25 Кофе

        FIGIES.put("FUTSILV03260", 1);
        FIGIES.put("FUTCOCOA0326", 1); //COCOA-9.25 Какао
        FIGIES.put("FUTIMOEXF000", 1);
*/

        /*
        FIGIES.put("FUTIMOEXF000", 1);

        FIGIES.put("FUTUSDRUBF00", 1);
        FIGIES.put("FUTIBIT03260", 1);
        FIGIES.put("FUTBR0126000", 1);

         */
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }

    public boolean isEnabled() { return false; }
}
