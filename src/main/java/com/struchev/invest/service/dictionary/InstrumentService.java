package com.struchev.invest.service.dictionary;

import com.struchev.invest.service.tinkoff.ITinkoffCommonAPI;
import com.struchev.invest.service.tinkoff.TinkoffGRPCAPI;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.Quotation;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to provide details for any trade instrument
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstrumentService {
    private final ITinkoffCommonAPI tinkoffCommonAPI;
    private Map<String, Instrument> instrumentByFigi;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Instrument {
        String figi;
        String tiket;
        String currency;
        String name;
        int lot;
        BigDecimal minPriceIncrement;
        Boolean isBuyAvailable;
    }

    public Instrument getInstrument(String figi) {
        var instrument = instrumentByFigi.get(figi);
        if (instrument == null) {
            var filtered = instrumentByFigi.values().stream().filter(v -> {
                //log.info("instrument {} ({}): {}", v.getFigi(), v.getTiket(), v);
                return v.getTiket().equals(figi) && v.getIsBuyAvailable();
            });
            if (filtered.count() == 1) {
                instrument = filtered.findFirst().orElse(null);
            }
        }
        if (instrument == null) {
            log.warn("Instrument not found: {}", figi);
        }
        return instrument;
    }

    @SneakyThrows
    @PostConstruct
    @Retryable
    private void init() {
        // загружаем все инструменты в память
        instrumentByFigi = new ConcurrentHashMap<>();

        var shares = tinkoffCommonAPI.getApi().getInstrumentsService().getAllSharesSync();
        shares.forEach(i -> instrumentByFigi.put(i.getFigi(), new Instrument(i.getFigi(), i.getTicker(), i.getCurrency(), i.getName(), i.getLot(), TinkoffGRPCAPI.toBigDecimal(i.getMinPriceIncrement(), 8), i.getBuyAvailableFlag())));

        var futures = tinkoffCommonAPI.getApi().getInstrumentsService().getAllFuturesSync();
        futures.forEach(i -> instrumentByFigi.put(i.getFigi(), new Instrument(i.getFigi(), i.getTicker(), i.getCurrency(), i.getName(), i.getLot(), TinkoffGRPCAPI.toBigDecimal(i.getMinPriceIncrement(), 8), i.getBuyAvailableFlag())));

        var bounds = tinkoffCommonAPI.getApi().getInstrumentsService().getAllBondsSync();
        bounds.forEach(i -> instrumentByFigi.put(i.getFigi(), new Instrument(i.getFigi(), i.getTicker(), i.getCurrency(), i.getName(), i.getLot(), TinkoffGRPCAPI.toBigDecimal(i.getMinPriceIncrement(), 8), i.getBuyAvailableFlag())));

        var etfs = tinkoffCommonAPI.getApi().getInstrumentsService().getAllEtfsSync();
        etfs.forEach(i -> instrumentByFigi.put(i.getFigi(), new Instrument(i.getFigi(), i.getTicker(), i.getCurrency(), i.getName(), i.getLot(), TinkoffGRPCAPI.toBigDecimal(i.getMinPriceIncrement(), 8), i.getBuyAvailableFlag())));

        var currencies = tinkoffCommonAPI.getApi().getInstrumentsService().getAllCurrenciesSync();
        currencies.forEach(i -> instrumentByFigi.put(i.getFigi(), new Instrument(i.getFigi(), i.getTicker(), i.getCurrency(), i.getName(), i.getLot(), TinkoffGRPCAPI.toBigDecimal(i.getMinPriceIncrement(), 8), i.getBuyAvailableFlag())));
    }
}
