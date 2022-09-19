package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossTubePersent02Strategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {
    public Double getMinPercentTubeMoveUp() { return -0.020; }
    public boolean isEnabled() { return false; }
}
