package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossNotCellStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {

    private Map FIGIES = Map.of(
            "BBG004S681W1", 10,    // МТС
            "BBG006L8G4H1", 1,    // Yandex
            "BBG00178PGX3", 3,    // VK
            "BBG005DXJS36", 5 // TCS Group (Tinkoff Bank holder)
            //"BBG005F1DK91", 2, // G1
            //"BBG000QGWY50", 6 // Bluebird Bio Inc
    );

    //public Map<String, Integer> getFigies() { return FIGIES; }
    public Boolean isNotCellIfBuy() { return true; }

    //public boolean isEnabled() { return true; }
    //public boolean isArchive() {return false;}
}
