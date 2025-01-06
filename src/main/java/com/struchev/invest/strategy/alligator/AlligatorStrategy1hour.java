package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy1hour extends AlligatorStrategy {
    public String getInterval() { return "1hour"; }
}
