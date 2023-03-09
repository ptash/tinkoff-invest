package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial2TopAll20ExtRiskStrategy extends Factorial2TopAll50ExtRiskStrategy {

    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 20;
    }
}
