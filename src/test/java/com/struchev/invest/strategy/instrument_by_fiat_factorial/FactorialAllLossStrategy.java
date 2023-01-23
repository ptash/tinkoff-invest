package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialAllLossStrategy extends Factorial2Strategy {
    public  AInstrumentByFiatFactorialStrategy.BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setIsAllUnderLoss(true);
        return buy;
    }
    public boolean isEnabled() {
        return true;
    }
}
