package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllOneStrategy extends Factorial3TopAllStrategy {
    public Integer getFactorialLengthFuture() { return 1; }
    public Boolean isFactorialAvgMaxMin() { return true; };
}
