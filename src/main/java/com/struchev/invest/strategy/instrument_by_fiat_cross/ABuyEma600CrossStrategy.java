package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public abstract class ABuyEma600CrossStrategy extends AInstrumentByFiatCrossStrategy {
    private Map FIGIES = Map.of(
            //"BBG004730RP0", 10, // Газпром
            //"BBG004S683W7", 10,   // Аэрофлот
            //"BBG00178PGX3", 1,    // VK
            //"BBG008NMBXN8", 1, // Robinhood
            //"BBG004NLQHL0", 1, // Fastly Inc
            //"BBG000BPWXK1", 1, // Newmont Goldcorp Corporation
            //"BBG006G2JVL2", 1, // Alibaba
            //"BBG000QGWY50", 5, // Bluebird Bio Inc
            "BBG005F1DK91", 1, // G1
            //"BBG002NLDLV8", 2, // VIPS
            "BBG0016XJ8S0", 4 // TAL Education Group
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
        return true;
    }
}
