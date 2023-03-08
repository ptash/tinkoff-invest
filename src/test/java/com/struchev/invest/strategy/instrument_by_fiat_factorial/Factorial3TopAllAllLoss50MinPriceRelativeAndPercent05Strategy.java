package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import org.springframework.stereotype.Component;

@Component
public class Factorial3TopAllAllLoss50MinPriceRelativeAndPercent05Strategy extends Factorial3TopAllAllLoss20MinPriceRelativeAndPercent05Strategy {
    public Integer getFactorialHistoryLength() { return this.getFactorialLength() * 50; }
}
