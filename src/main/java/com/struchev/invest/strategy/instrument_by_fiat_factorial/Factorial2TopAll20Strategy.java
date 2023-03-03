package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Factorial2TopAll20Strategy extends Factorial2TopAllStrategy {
    public Integer getFactorialHistoryLength() {
        return this.getFactorialLength() * 20;
    }
}
