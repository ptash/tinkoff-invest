package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleFactorOnlyUp20SimStrategy extends FactorialCandleFactorOnlyUp20Strategy {

    public Boolean isFactorialSimple() { return true; }
    public Integer getPriceDiffAvgLength() { return 0; }
    public Float getPriceDiffAvg() { return 1f; }

    public boolean isEnabled() { return true; }
}
