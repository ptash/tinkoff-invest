package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleFactorOnlyUp20Buy3Strategy extends FactorialCandleFactorOnlyUp20Strategy {

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setCandleUpMinLength(3);
        return buy;
    }

    public boolean isEnabled() { return true; }
}
