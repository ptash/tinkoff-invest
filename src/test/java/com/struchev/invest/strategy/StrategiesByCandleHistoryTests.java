package com.struchev.invest.strategy;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.repository.CandleRepository;
import com.struchev.invest.repository.OrderRepository;
import com.struchev.invest.service.candle.CandleHistoryService;
import com.struchev.invest.service.order.OrderService;
import com.struchev.invest.service.processor.PurchaseService;
import com.struchev.invest.service.report.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;

import static org.junit.Assert.assertTrue;

@SpringBootTest
@Slf4j
@ActiveProfiles(profiles = "test")
class StrategiesByCandleHistoryTests {
    @Autowired
    CandleRepository candleRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    PurchaseService purchaseService;
    @Autowired
    OrderService orderService;
    @Autowired
    ReportService reportService;
    @Autowired
    StrategySelector strategySelector;

    @Value("${test.candle.history.duration}")
    private Duration historyDuration;
    //@Value("${test.candle.history.dateBefore}")
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2022-09-13T03:00:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2022-09-16T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2022-09-23T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2022-11-08T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2023-01-15T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2022-12-26T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2022-11-14T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2022-10-03T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2023-01-24T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2023-01-30T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2023-02-18T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2023-02-23T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2023-02-25T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2023-03-04T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2023-03-08T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    //private OffsetDateTime dateBefore = OffsetDateTime.parse("2023-03-10T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    private OffsetDateTime dateBefore = OffsetDateTime.parse("2023-03-15T01:30:00+03:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);


    @Value("${tinkoff.emulator}")
    private Boolean isTinkoffEmulator;

    @BeforeEach
    public void clean() {
        // удаляем существующие ордеры из БД
        orderRepository.deleteAll();
        orderService.loadOrdersFromDB();
    }

    @Test
    void checkProfitByStrategies() {
        // Проверяем свойства для тестов
        assertTrue("Tests are allowed with Tinkoff API emulator only (tinkoff.emulator=true)", isTinkoffEmulator);

        // Эмулируем поток свечей за заданный интервал (test.candle.history.duration)
        var days = historyDuration.toDays();
        var strategies = strategySelector.getFigiesForActiveStrategies();
        log.info("Эмулируем поток свечей за заданный интервал в днях {} до {} for {} strategies", days, dateBefore, strategies.size());

        strategies.stream()
                .flatMap(figi -> {
                    var candles = candleRepository.findByFigiAndIntervalAndBeforeDateTimeLimit(figi,
                            "1min", dateBefore, PageRequest.of(0, 1));
                    if (candles == null || candles.size() == 0) {
                        log.info("getFigiesForActiveStrategies cancel {}: getCandlesByFigiByLength return {}", figi, candles);
                        return new ArrayList<CandleDomainEntity>().stream();
                    }
                    var startDateTime = candles.get(0).getDateTime().minusDays(days);
                    return candleRepository.findByFigiAndIntervalAndDateTimeAfterAndDateTimeBeforeOrderByDateTime(figi, "1min", startDateTime, dateBefore).stream();
                })
                .sorted(Comparator.comparing(CandleDomainEntity::getDateTime))
                .forEach(c -> purchaseService.observeNewCandle(c));

        // Логируем отчеты
        reportService.logReportInstrumentByFiat(reportService.buildReportInstrumentByFiat());
        reportService.logReportInstrumentByInstrument(reportService.buildReportInstrumentByInstrument());
        assert true;
    }

}