package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestMix2Strategy extends BuyProfit1InvestMixStrategy {

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
}
