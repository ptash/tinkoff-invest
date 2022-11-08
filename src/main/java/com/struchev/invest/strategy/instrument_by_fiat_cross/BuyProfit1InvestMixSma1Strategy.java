package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyProfit1InvestMixSma1Strategy extends BuyProfit1InvestMixStrategy {
    private Map FIGIES = Map.of(
            "BBG005DXJS36", 1, // TCS Group (Tinkoff Bank holder)
            "BBG004S68CP5", 5   // М.видео
            /*
            "BBG003QBJKN0", 4, // Allakos Inc
            "BBG001KS9450", 10    // 2U Inc*/
    );

    public Map<String, Integer> getFigies() {
        return FIGIES;
    }

    @Autowired
    private BuyProfit1InvestPercent02SellWithMaxProfitPercentFromSma1CrossSimpleStrategy investStrategy;

    @Qualifier("buyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy")
    @Autowired
    private BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy crisisStrategy;

    public AInstrumentByFiatCrossStrategy getInvestStrategy() {
        return investStrategy;
    }

    public AInstrumentByFiatCrossStrategy getCrisisStrategy() {
        return crisisStrategy;
    }

    public boolean isEnabled() { return true; }
    public boolean isArchive() {return true;}
}
