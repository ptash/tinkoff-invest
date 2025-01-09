package com.struchev.invest.strategy.alligator;

import org.springframework.stereotype.Component;

@Component
public class AlligatorStrategy15Min extends AlligatorStrategy {
    public String getInterval() { return "15min"; }

    public boolean isEnabled() { return false; }
}
