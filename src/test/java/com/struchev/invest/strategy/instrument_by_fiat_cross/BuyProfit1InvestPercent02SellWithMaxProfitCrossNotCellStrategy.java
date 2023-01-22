package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossNotCellStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {

    public Boolean isNotCellIfBuy() { return true; }

    public boolean isEnabled() { return false; }
}
