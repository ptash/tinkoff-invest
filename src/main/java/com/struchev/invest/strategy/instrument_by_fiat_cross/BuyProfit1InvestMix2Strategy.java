package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestMix2Strategy extends BuyProfit1InvestMixStrategy {

    private Map FIGIES = Map.of(
            "BBG004730N88", 10, // Сбер
            "BBG00475KKY8", 2, // НОВАТЭК
            "BBG006L8G4H1", 3    // Yandex
            /*
            "BBG006G2JVL2", 1, // Alibaba
            "BBG000J3D1Y8", 6 // OraSure Technologies Inc
             */
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }
    @Qualifier("buyProfit1InvestPercent02SellWithMaxProfitCrossTube2Strategy")
    @Autowired
    private BuyProfit1InvestPercent02SellWithMaxProfitCrossTube2Strategy investStrategy;
    @Qualifier("buyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy")
    @Autowired
    private BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy crisisStrategy;

    public AInstrumentByFiatCrossStrategy getInvestStrategy() {
        return investStrategy;
    }

    public AInstrumentByFiatCrossStrategy getCrisisStrategy() {
        return crisisStrategy;
    }

    public Double getInvestPercentFromSmaSlowest() { return getDeadLinePercentFromSmaSlowest() / 2; }

    public boolean isEnabled() { return true; }
    public boolean isArchive() { return true; }
}
