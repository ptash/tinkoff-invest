package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitPercentFromSma1CrossSimpleStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitPercentFromSma1CrossStrategy {

    public Boolean isTubeAvgDeltaSimple() { return true; }
}
