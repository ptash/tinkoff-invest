package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleFactorOnlyUp20PrevSizeStrategy extends FactorialCandleFactorOnlyUp20Strategy {

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        //buy.setCandleUpMinLength(3);
        buy.setIsUpMaxPercentSeePrevSize(150f);
        buy.setIsDownWithLimits(true);
        return buy;
    }

    public boolean isEnabled() { return true; }
}
