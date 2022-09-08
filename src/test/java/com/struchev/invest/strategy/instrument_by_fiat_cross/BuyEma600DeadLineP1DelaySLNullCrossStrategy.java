package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BuyEma600DeadLineP1DelaySLNullCrossStrategy extends BuyEma600DeadLineP1CrossStrategy {
    public Duration getDelayBySL() {
        return null;
    }
    public Duration getDelayPlusBySL() { return Duration.ofHours(4); }
}
