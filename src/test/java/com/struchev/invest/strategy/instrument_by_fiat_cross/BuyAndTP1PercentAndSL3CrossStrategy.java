package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyAndTP1PercentAndSL3CrossStrategy extends AInstrumentByFiatCrossStrategy {
    private Map FIGIES = Map.of(
            //"BBG004S683W7", 10,   // Аэрофлот
            //"BBG00178PGX3", 1,    // VK
            //"BBG008NMBXN8", 1, // Robinhood
            "BBG005F1DK91", 1 // G1
            //"BBG002NLDLV8", 1, // VIPS
            //"BBG000BLY663", 1, // CROCS
            //"BBG00W0KZD98", 1  //LI
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }

    @Override
    public BuyCriteria getBuyCriteria() {
        return BuyCriteria.builder().lessThenPercentile(5).build();
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
