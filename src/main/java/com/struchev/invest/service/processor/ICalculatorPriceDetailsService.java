package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.strategy.AStrategy;

import java.math.BigDecimal;
import java.util.Map;

interface ICalculatorPriceDetailsService {
    Map<String, BigDecimal> getCurrentPrices();
}
