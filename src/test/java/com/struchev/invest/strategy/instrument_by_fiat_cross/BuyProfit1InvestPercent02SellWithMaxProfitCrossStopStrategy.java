package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossStopStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {

    public Boolean allowBuyUnderSmaTube() { return false; }

    public boolean isEnabled() { return false; }
}
