package com.struchev.invest.strategy.instrument_by_fiat;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyP10AndTP1PercentAndSL3PercentStrategy extends AInstrumentByFiatStrategy {

    private Map FIGIES = Map.of(
            //"BBG004S683W7", 10,   // Аэрофлот
            //"BBG00178PGX3", 1,    // VK
            //"BBG008NMBXN8", 1, // Robinhood
            //"BBG005F1DK91", 1, // G1
            //"BBG002NLDLV8", 1, // VIPS
            //"BBG000BLY663", 1, // CROCS
            //"BBG00W0KZD98", 1  //LI
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }

    @Override
    public AInstrumentByFiatStrategy.BuyCriteria getBuyCriteria() {
        return AInstrumentByFiatStrategy.BuyCriteria.builder().lessThenPercentile(5).build();
    }

    @Override
    public AInstrumentByFiatStrategy.SellCriteria getSellCriteria() {
        return AInstrumentByFiatStrategy.SellCriteria.builder().takeProfitPercent(1f).stopLossPercent(3f).build();
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
