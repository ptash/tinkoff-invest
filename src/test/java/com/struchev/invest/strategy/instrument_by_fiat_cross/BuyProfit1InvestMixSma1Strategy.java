package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BuyProfit1InvestMixSma1Strategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy {

    AInstrumentByFiatCrossStrategy investStrategy;// = new BuyProfit1InvestPercent02SellWithMaxProfitPercentFromSma1CrossSimpleStrategy();

    public AInstrumentByFiatCrossStrategy getInvestStrategy() {
        if (null == investStrategy) {
            investStrategy = new BuyProfit1InvestPercent02SellWithMaxProfitPercentFromSma1CrossSimpleStrategy();
        }
        return investStrategy;
    }
}
