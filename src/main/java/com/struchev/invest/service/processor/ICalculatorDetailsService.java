package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.strategy.AStrategy;

import java.math.BigDecimal;
import java.util.Map;

interface ICalculatorDetailsService {
    public Map<String, Boolean> getOrderBooleanDataMap(AStrategy strategy, CandleDomainEntity candle);
    public Map<String, BigDecimal> getOrderBigDecimalDataMap(AStrategy strategy, CandleDomainEntity candle);
}
