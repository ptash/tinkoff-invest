package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AlligatorStrategy5MinALimit extends AlligatorStrategy5Min {
    public Double getLimitPercentByCandle() { return -1.; }
    //public Boolean isLimitPercentByPriceAlligator() { return true; }
}
