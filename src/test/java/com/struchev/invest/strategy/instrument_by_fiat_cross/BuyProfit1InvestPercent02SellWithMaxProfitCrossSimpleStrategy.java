package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossSimpleStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {
    public Boolean isTubeAvgDeltaSimple() { return true; }
}
