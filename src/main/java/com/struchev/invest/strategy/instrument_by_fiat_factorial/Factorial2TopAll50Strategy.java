package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Factorial2TopAll50Strategy extends Factorial2TopAllStrategy {
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 50;
    }
}
