package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleFactorOnlyUp20CopyStrategy extends FactorialCandleFactorOnlyUp20Strategy {
    public boolean isEnabled() { return true; }
}
