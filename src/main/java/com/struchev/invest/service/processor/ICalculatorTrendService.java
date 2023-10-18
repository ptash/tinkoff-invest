package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.strategy.AStrategy;

import java.math.BigDecimal;

interface ICalculatorTrendService<T extends AStrategy> {
    boolean isTrendBuy(T strategy, CandleDomainEntity candle);
    boolean isTrendSell(T strategy, CandleDomainEntity candle);
}
