package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial2TopAll200Strategy extends Factorial2TopAllStrategy {
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 200;
    }
}
