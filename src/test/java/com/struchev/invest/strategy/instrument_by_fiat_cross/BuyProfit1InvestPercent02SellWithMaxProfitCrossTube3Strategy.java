package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossTube3Strategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossTube2Strategy {
    public Boolean isTubeAvgDeltaAdvance3() { return true; }

    public boolean isEnabled() { return true; }
}
