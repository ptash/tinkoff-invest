package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitPercentFromSma1CrossStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {

    private Map FIGIES = Map.of(
/*
            "BBG006L8G4H1", 1,   // Yandex
            "BBG000LNHHJ9", 10, // КАМАЗ
            "BBG004S683W7", 90,   // Аэрофлот
            "BBG000LWVHN8", 6000, // Дагестанская энергосбытовая компания
            "BBG000QFH687", 100000   // ТГК-1
*/
            "BBG005DXJS36", 1, // TCS Group (Tinkoff Bank holder)
            "BBG000QGWY50", 3, // Bluebird Bio Inc
            //"BBG001KS9450", 3, // 2U Inc
            "BBG000BPWXK1", 1 // Newmont Goldcorp Corporation

    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }
    public Double getDeadLinePercentFromSmaSlowest() { return 1.0; }
    public Double getDeadLinePercent() { return 0.5; }

    public boolean isEnabled() { return true; }

    public boolean isArchive() { return true; }
}
