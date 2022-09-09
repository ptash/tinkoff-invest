package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

@Component
public class BuyEma600DeadLineP1CrossStrategy extends ABuyEma600CrossStrategy {
    public Double getDeadLinePercent() { return 1.0; }

    public boolean isEnabled() { return true; }
}
