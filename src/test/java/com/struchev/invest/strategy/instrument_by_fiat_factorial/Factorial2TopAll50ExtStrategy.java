package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial2TopAll50ExtStrategy extends Factorial2TopAll20ExtStrategy {

    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 50;
    }
}
