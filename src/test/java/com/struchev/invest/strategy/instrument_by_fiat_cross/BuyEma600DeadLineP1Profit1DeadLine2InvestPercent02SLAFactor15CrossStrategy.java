package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02SLAFactor15CrossStrategy extends BuyEma600DeadLineP1Profit1DeadLine2InvestPercent02CrossStrategy {

    public Boolean isBuyInvestCrossSmaEma2() { return true; }

    public Integer getDelayPlusBySL() { return 6 * 60; }

    public Double getDelayPlusBySLFactor() { return 1.0; }
    public Double getMinPercentTubeMoveUp() { return -0.020; }

    @Override
    public boolean isEnabled() { return true; }
}
