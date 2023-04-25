package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class FactorialCandleFactor20MinMin2Strategy extends FactorialCandleFactor20Strategy {

    public BuyCriteria getBuyCriteria() {
        var buy = super.getBuyCriteria();
        buy.setCandleDownMinMinPointLength(3);
        buy.setCandleDownMinMinMaxLength(2);
        return buy;
    }
}
