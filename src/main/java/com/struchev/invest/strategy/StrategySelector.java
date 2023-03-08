package com.struchev.invest.strategy;

import com.struchev.invest.service.dictionary.InstrumentService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategySelector {
    private final InstrumentService instrumentService;
    @Getter
    private final List<AStrategy> allStrategies;
    private List<AStrategy> activeStrategies;

    @Autowired
    private StrategySettings config;

    public List<AStrategy> suitableByFigi(String figi, AStrategy.Type type, String interval) {
        return activeStrategies.stream()
                .filter(s -> s.isEnabled())
                .filter(s -> type == null || s.getType() == type)
                .filter(s -> interval == null || s.getInterval().equals(interval))
                .filter(s -> s.isSuitableByFigi(figi))
                .collect(Collectors.toList());
    }

    public List<AStrategy> getActiveStrategies() {
        return activeStrategies;
    }

    public Set<String> getFigiesForActiveStrategies() {
        return getActiveStrategies().stream().flatMap(s -> filterByCurrency(s.getFigies())).collect(Collectors.toSet());
    }

    public Set<InstrumentService.Instrument> getInstrumentsForActiveStrategies() {
        return getFigiesForActiveStrategies().stream().map(figi -> instrumentService.getInstrument(figi)).collect(Collectors.toSet());
    }

    public Stream<String> filterByCurrency(Map<String, Integer> figies) {
        return figies.keySet().stream().filter(figi -> {
            var instrument = instrumentService.getInstrument(figi);
            log.info("Figi {} currency {}. Target currency {}", figi, instrument.getCurrency(), config.getCurrencies());
            instrumentService.printInstrumentInfo(instrument);
            return config.getCurrencies().contains(instrument.getCurrency().toUpperCase());
        });
    }

    public AStrategy.Type getStrategyType(String name, AStrategy.Type defaultValue) {
        return allStrategies.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst().map(AStrategy::getType).orElse(defaultValue);
    }

    @PostConstruct
    private void init() {
        activeStrategies = allStrategies.stream()
                .filter(s -> s.isEnabled())
                .peek(s -> {
                    if (s.getType() == AStrategy.Type.instrumentByInstrument && s.getFigies().size() < 2
                            || s.getType() == AStrategy.Type.instrumentByFiat && s.getFigies().size() < 1) {
                        throw new RuntimeException("Incorrect count of figies in " + s.getName());
                    }
                })
                .peek(s -> log.info("Enabled strategy: {}: {}", s.getName(), s.getFigies().keySet()))
                .collect(Collectors.toList());
    }
}
