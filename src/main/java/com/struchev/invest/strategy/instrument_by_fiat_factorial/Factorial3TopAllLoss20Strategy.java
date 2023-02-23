package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllLoss20Strategy extends Factorial3TopAllLoss200Strategy {

    public Integer getFactorialHistoryLength() { return this.getFactorialLength() * 20; }
}
