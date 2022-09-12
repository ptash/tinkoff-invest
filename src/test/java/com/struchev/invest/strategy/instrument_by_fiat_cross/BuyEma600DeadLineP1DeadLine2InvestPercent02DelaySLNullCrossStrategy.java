package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BuyEma600DeadLineP1DeadLine2InvestPercent02DelaySLNullCrossStrategy extends BuyEma600DeadLineP1DeadLine2InvestPercent02CrossStrategy {

    public Duration getDelayBySL() {
        return null;
    }
    public Integer getDelayPlusBySL() { return 4 * 60; }

    public boolean isEnabled() { return true; }
}
