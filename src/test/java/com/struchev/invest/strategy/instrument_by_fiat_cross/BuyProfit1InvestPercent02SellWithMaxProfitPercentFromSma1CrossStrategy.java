package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitPercentFromSma1CrossStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {

    public Double getDeadLinePercentFromSmaSlowest() { return 1.0; }
    public Double getDeadLinePercent() { return 0.5; }
}
