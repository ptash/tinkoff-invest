package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitSellEma2DownCrossStrategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossStrategy {

    public Boolean isSellEma2UpOnBottom() { return false; }
}
