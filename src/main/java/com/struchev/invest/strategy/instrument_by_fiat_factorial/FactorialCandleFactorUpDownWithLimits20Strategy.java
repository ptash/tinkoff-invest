package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleFactorUpDownWithLimits20Strategy extends FactorialCandleFactorOnlyUp20Strategy {

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        //buy.setCandleUpMinLength(3);
        //buy.setIsUpMaxPercentSeePrevSize(150f);
        buy.setIsDownWithLimits(true);
        buy.setDownStopLossFactor(2f);
        return buy;
    }

    public boolean isEnabled() { return true; }
}
