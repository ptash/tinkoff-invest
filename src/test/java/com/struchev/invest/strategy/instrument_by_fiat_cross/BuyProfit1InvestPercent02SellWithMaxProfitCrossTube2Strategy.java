package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BuyProfit1InvestPercent02SellWithMaxProfitCrossTube2Strategy extends BuyProfit1InvestPercent02SellWithMaxProfitCrossTubeStrategy {
    public Boolean isTubeAvgDeltaAdvance2() { return true; }

    public Integer getAvgLength() {return getSmaFastLength() / 2;}

    public boolean isEnabled() { return false; }
}
