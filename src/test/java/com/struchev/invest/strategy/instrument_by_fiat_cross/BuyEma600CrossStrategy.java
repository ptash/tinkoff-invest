package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BuyEma600CrossStrategy extends AInstrumentByFiatCrossStrategy {
    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        FIGIES.put("BBG004730RP0", 10); // Газпром
        FIGIES.put("BBG004S683W7", 10);   // Аэрофлот
        FIGIES.put("BBG00178PGX3", 1);    // VK
        FIGIES.put("BBG00475KKY8", 1); // НОВАТЭК
        FIGIES.put("BBG004S68BH6", 1); // ПИК
        FIGIES.put("BBG000LWVHN8", 1000); // Дагестанская энергосбытовая компания
        //"BBG008NMBXN8", 1, // Robinhood
        FIGIES.put("BBG004NLQHL0", 1); // Fastly Inc
        FIGIES.put("BBG000BPWXK1", 1); // Newmont Goldcorp Corporation
        FIGIES.put("BBG000QGWY50", 1); // Bluebird Bio Inc
        FIGIES.put("BBG006G2JVL2", 1); // Alibaba
        FIGIES.put("BBG005F1DK91", 1); // G1
        FIGIES.put("BBG002NLDLV8", 1); // VIPS
        FIGIES.put("BBG000BLY663", 1); // CROCS
        FIGIES.put("BBG000J3D1Y8", 1); // OraSure Technologies Inc")
        FIGIES.put("BBG0016XJ8S0", 1); // TAL Education Group
        FIGIES.put("BBG000BR85F1", 1); // PetroChina
        FIGIES.put("BBG00W0KZD98", 1); //LI
    }

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }

    @Override
    public BuyCriteria getBuyCriteria() {
        return BuyCriteria.builder().lessThenPercentile(5).build();
    }

    public Integer getSmaSlowestLength() {
        return 600;
    }

    public Integer getSmaSlowLength() {
        return 150;
    }

    public Integer getSmaFastLength() {
        return 60;
    }

    public Integer getEmaFastLength() {
        return 60;
    }

    @Override
    public SellCriteria getSellCriteria() {
        return SellCriteria.builder().takeProfitPercent(2f).stopLossPercent(3f).build();
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
