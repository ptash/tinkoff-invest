package com.struchev.invest.service.notification;

import com.struchev.invest.strategy.AStrategy;

import java.time.Duration;

public class StrategyShort extends AStrategy {
    private String name;

    public StrategyShort(String s) {
        name = s;
    }

    public String getName() {
        return name;
    }

    @Override
    public Duration getDelayBySL() {
        return null;
    }

    @Override
    public Type getType() {
        return null;
    }
}
