package com.struchev.invest.service.processor;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.strategy.AStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис - фасад для калькуляторов по разным стратегиям
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalculatorFacade {
    private final List<ICalculatorService> calculateServices;
    private Map<AStrategy.Type, ICalculatorService> calculateServiceByType;

    public <T extends AStrategy> boolean isShouldBuy(T strategy, CandleDomainEntity candle) {
        try {
            return calculateServiceByType.get(strategy.getType()).isShouldBuy(strategy, candle);
        } catch (NullPointerException e) {
            log.info("error in strategy " + strategy.getName(), e);
            throw e;
        }
    }

    public <T extends AStrategy> boolean isShouldSell(T strategy, CandleDomainEntity candle, BigDecimal purchaseRate) {
        try {
            return calculateServiceByType.get(strategy.getType()).isShouldSell(strategy, candle, purchaseRate);
        } catch (NullPointerException e) {
            log.info("error in strategy " + strategy.getName(), e);
            throw e;
        }
    }

    @PostConstruct
    private void init() {
        calculateServiceByType = calculateServices.stream().collect(Collectors.toMap(c -> c.getStrategyType(), c -> c));
    }
}
