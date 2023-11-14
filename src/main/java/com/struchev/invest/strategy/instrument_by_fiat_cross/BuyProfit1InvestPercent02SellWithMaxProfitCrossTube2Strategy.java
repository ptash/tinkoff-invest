package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossTube2Strategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy {
    private Map FIGIES = Map.of(
            /*"BBG002W2FT69", 10, // АбрауДюрсо
            "BBG000LWVHN8", 20000, // Дагестанская энергосбытовая компания
            "BBG000LNHHJ9", 10, // КАМАЗ
            "BBG004S68BH6", 2, // Пик
            "BBG004S681W1", 20,    // МТС
            "BBG00178PGX3", 12    // VK

            "BBG000BLY663", 1, // CROCS
            "BBG004NLQHL0", 2 // Fastly Inc*/
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }
    public Boolean isTubeAvgDeltaAdvance2() { return true; }

    public Integer getAvgLength() {return getSmaFastLength() / 2;}

    public boolean isEnabled() { return false; }
    public boolean isArchive() {return true;}
}
