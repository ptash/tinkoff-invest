package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02SLAFactor15CrossStrategy extends BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02CrossStrategy {

    private Map FIGIES = Map.of(
            "BBG006L8G4H1", 1,   // Yandex
            "BBG004TC84Z8", 10,   // Трубная Металлургическая Компания
            "BBG004S68CP5", 5,   // М.видео
            "BBG000QFH687", 100000,   // ТГК-1
            "BBG004S683W7", 90,   // Аэрофлот
            "BBG004S68696", 10,    // Распадская
            "BBG00475KKY8", 2 // НОВАТЭК

    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }

    public Boolean isBuyInvestCrossSmaEma2() { return true; }

    public Integer getDelayPlusBySL() { return 6 * 60; }

    public Double getDelayPlusBySLFactor() { return 1.0; }
    public Double getMinPercentTubeMoveUp() { return -0.020; }

    @Override
    public boolean isEnabled() { return false; }
}
