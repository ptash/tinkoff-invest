package com.struchev.invest.strategy.instrument_by_fiat_cross;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuyEma600DeadLineP1CrossStrategy extends BuyEma600CrossStrategy {
    public Double getDeadLinePercent() { return 1.0; }

    @Override
    public boolean isEnabled() { return false; }
}
