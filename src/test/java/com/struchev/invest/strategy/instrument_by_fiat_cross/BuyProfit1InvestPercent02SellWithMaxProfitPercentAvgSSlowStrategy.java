package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitPercentAvgSSlowStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {

    public Integer getAvgLength() {
        return getSmaSlowLength();
    }
    public boolean isEnabled() { return false; }
}
