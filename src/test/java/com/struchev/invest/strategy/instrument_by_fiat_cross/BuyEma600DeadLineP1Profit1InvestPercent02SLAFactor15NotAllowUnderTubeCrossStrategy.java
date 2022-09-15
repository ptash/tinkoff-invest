package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyEma600DeadLineP1Profit1InvestPercent02SLAFactor15NotAllowUnderTubeCrossStrategy extends BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02SLAFactor15CrossStrategy {

    public Boolean allowBuyUnderSmaTube() { return false; }

    public boolean isEnabled() { return false; }
}
