package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestMixDelaySLNullStrategy extends BuyProfit1InvestMixStrategy {

    @Qualifier("buyEma600DeadLineP1DeadLine2InvestPercent01DelaySLNullCrossStrategy")
    @Autowired
    private BuyEma600DeadLineP1DeadLine2InvestPercent01DelaySLNullCrossStrategy investStrategy;

    @Qualifier("buyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy")
    @Autowired
    private BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy crisisStrategy;

    public AInstrumentByFiatCrossStrategy getInvestStrategy() {
        return investStrategy;
    }

    public AInstrumentByFiatCrossStrategy getCrisisStrategy() {
        return crisisStrategy;
    }

    //public Double getInvestPercentFromSmaSlowest() { return -getDeadLinePercentFromSmaSlowest(); }

    public boolean isEnabled() { return true; }
}
