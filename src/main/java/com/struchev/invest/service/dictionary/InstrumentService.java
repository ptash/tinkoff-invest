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
        String currency;
        String name;
        int lot;
        BigDecimal minPriceIncrement;
    }

    public Instrument getInstrument(String figi) {
        var instrument = instrumentByFigi.get(figi);
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
        shares.forEach(i -> instrumentByFigi.put(i.getFigi(), new Instrument(i.getFigi(), i.getCurrency(), i.getName(), i.getLot(), TinkoffGRPCAPI.toBigDecimal(i.getMinPriceIncrement(), 8))));

        var futures = tinkoffCommonAPI.getApi().getInstrumentsService().getAllFuturesSync();
        futures.forEach(i -> instrumentByFigi.put(i.getFigi(), new Instrument(i.getFigi(), i.getCurrency(), i.getName(), i.getLot(), TinkoffGRPCAPI.toBigDecimal(i.getMinPriceIncrement(), 8))));

        var bounds = tinkoffCommonAPI.getApi().getInstrumentsService().getAllBondsSync();
        bounds.forEach(i -> instrumentByFigi.put(i.getFigi(), new Instrument(i.getFigi(), i.getCurrency(), i.getName(), i.getLot(), TinkoffGRPCAPI.toBigDecimal(i.getMinPriceIncrement(), 8))));

        var etfs = tinkoffCommonAPI.getApi().getInstrumentsService().getAllEtfsSync();
        etfs.forEach(i -> instrumentByFigi.put(i.getFigi(), new Instrument(i.getFigi(), i.getCurrency(), i.getName(), i.getLot(), TinkoffGRPCAPI.toBigDecimal(i.getMinPriceIncrement(), 8))));

        var currencies = tinkoffCommonAPI.getApi().getInstrumentsService().getAllCurrenciesSync();
        currencies.forEach(i -> instrumentByFigi.put(i.getFigi(), new Instrument(i.getFigi(), i.getCurrency(), i.getName(), i.getLot(), TinkoffGRPCAPI.toBigDecimal(i.getMinPriceIncrement(), 8))));
    }
}
