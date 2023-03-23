package com.struchev.invest.webscraping;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.struchev.invest.entity.ContractResultEntity;
import com.struchev.invest.entity.CurrencyRateEntity;
import com.struchev.invest.entity.InstrumentEntity;
import com.struchev.invest.repository.ContractResultRepository;
import com.struchev.invest.repository.CurrencyRateRepository;
import com.struchev.invest.repository.InstrumentRepository;
import com.struchev.invest.repository.OrderRepository;
import com.struchev.invest.service.dictionary.InstrumentService;
import com.struchev.invest.strategy.StrategySelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.json.JSONParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@DependsOn({"instrumentService"})
@RequiredArgsConstructor
public class MoexScrapingTask {

    private final CurrencyRateRepository currencyRateRepository;
    private final OrderRepository orderRepository;
    private final InstrumentRepository instrumentRepository;
    private final ContractResultRepository contractResultRepository;
    private final StrategySelector strategySelector;
    private final InstrumentService instrumentService;

    @Value("${moex.scraping.enabled:true}")
    Boolean isEnabled;

    @Scheduled(cron = "5,10,15 11,16 * * *")
    public void getDerivativeUsdRates() {
        if (!isEnabled) {
            return;
        }
        String from = "USD";
        String to = "RUB";
        String exchangeType = String.format("%s/%s", from, to);
        String urlString = String.format(
                "https://www.moex.com/export/derivatives/currency-rate.aspx?language=ru&currency=%s&moment_start=%s&moment_end=%s",
                exchangeType,
                OffsetDateTime.now().minusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        );
        log.info("Getting moex derivative usd rates from {}", urlString);
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();

            URL url = new URL(urlString);
            InputStream stream = url.openStream();
            var doc = docBuilder.parse(stream);
            doc.getDocumentElement().normalize();
            if (!doc.getDocumentElement().getAttribute("exchange-type").equals(exchangeType)) {
                throw new Exception(String.format("exchange-type is not as expected: %s != %s", exchangeType, doc.getDocumentElement().getAttribute("exchange-type")));
            }
            NodeList list = doc.getElementsByTagName("rates").item(0).getChildNodes();
            for (int temp = 0; temp < list.getLength(); temp++){

                Node node = list.item(temp);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) node;
                String moment = element.getAttribute("moment");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                TemporalAccessor acc = formatter.parse(moment);
                LocalDateTime.from(acc);
                OffsetDateTime dateTime = LocalDateTime.from(acc).atOffset(ZoneId.systemDefault().getRules().getOffset(Instant.now()));
                BigDecimal rate = new BigDecimal(element.getAttribute("value"));
                log.info("Parsed moex derivative usd rate: {} {}", dateTime, rate);
                var currencyRate = currencyRateRepository.findByFromCurrencyAndToCurrencyAndDateTime(from, to, dateTime);
                if (currencyRate != null) {
                    if (currencyRate.getRate().compareTo(rate) != 0) {
                        log.info("Update moex derivative usd rate: {} {} => {}", dateTime, currencyRate.getRate(), rate);
                        currencyRate.setRate(rate);
                        currencyRateRepository.save(currencyRate);
                    }
                    continue;
                }
                log.info("New moex derivative usd rate: {} {}", dateTime, rate);
                currencyRate = CurrencyRateEntity.builder()
                        .fromCurrency(from)
                        .toCurrency(to)
                        .dateTime(dateTime)
                        .rate(rate)
                        .build();
                currencyRateRepository.save(currencyRate);
            }
        } catch (Exception e) {
            var msg = String.format("An error during get moex derivative usd rates from %s new candle %s", urlString, e.getMessage());
            log.error(msg, e);
        }
    }

    @Scheduled(cron = "6,11,16 16,18 * * *")
    public void getContractResults() {
        if (!isEnabled) {
            return;
        }
        var futures = getActiveFutures();
        for (var i = 0; i < futures.size(); i++) {
            var future = futures.get(i);
            var instrument = getFutureInstrumentEntity(future);
            if (instrument.getMoexCode() == null || instrument.getMoexCode().isEmpty()) {
                log.error("Getting moex contract results fail for {}: empty moexCode", instrument.getFigi());
                continue;
            }
            String urlString = String.format(
                    "https://www.moex.com/ru/derivatives/contractresults-exp.aspx?day1=%s&day2=%s&code=%s",
                    OffsetDateTime.now().minusDays(30).format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                    OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                    instrument.getMoexCode()
            );
            log.info("Getting moex contract results for {} ({}) from {}", instrument.getFigi(), instrument.getMoexCode(), urlString);
            try {
                URL url = new URL(urlString);
                InputStream stream = url.openStream();
                Reader reader = new InputStreamReader(stream, "Windows-1251");
                CSVReader cvsReader = new CSVReaderBuilder(reader)
                        .withSkipLines(1) // skip first
                        .build();
                String [] nextLine;
                while ((nextLine = cvsReader.readNext()) != null) {
                    if (nextLine.length < 3) {
                        continue;
                    }
                    String date = nextLine[0];
                    String amount = nextLine[2];
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                    TemporalAccessor acc = formatter.parse(date);
                    OffsetDateTime dateTime = LocalDate.from(acc).atStartOfDay().atOffset(ZoneId.systemDefault().getRules().getOffset(Instant.now()));
                    dateTime = dateTime.plusHours(19);
                    BigDecimal price = new BigDecimal(amount.replace(",", "."));
                    saveContractResult(instrument, dateTime, price);
                }
            } catch (Exception e) {
                var msg = String.format("An error during get moex contract results from %s: %s", urlString, e.getMessage());
                log.error(msg, e);
            }
        }
    }

    @Scheduled(cron = "6,11,16 11,12 * * *")
    public void getDayContractResults() {
        if (!isEnabled) {
            return;
        }
        var futures = getActiveFutures();
        for (var i = 0; i < futures.size(); i++) {
            var future = futures.get(i);
            var instrument = getFutureInstrumentEntity(future);
            if (instrument.getMoexCode() == null || instrument.getMoexCode().isEmpty()) {
                log.error("Getting moex page contract result fail for {}: empty moexCode", instrument.getFigi());
                continue;
            }
            if (future.getTiket() == null || future.getTiket().isEmpty()) {
                log.error("Getting moex page contract result fail for {}: empty tiket", instrument.getFigi());
                continue;
            }
            var secTypes = future.getTiket().substring(0, 2).toUpperCase();
            String urlString = String.format(
                    "https://iss.moex.com/iss/engines/futures/markets/forts/securities/.jsonp?iss.meta=off&iss.json=extended&lang=ru&sectypes=%s",
                    secTypes
            );
            log.info("Getting moex page contract result for {} ({}) from {}", instrument.getFigi(), secTypes, urlString);
            try {
                ContractResultEntity contractResult = null;
                List<String> shortnames = new ArrayList<>();
                URL url = new URL(urlString);
                InputStream stream = url.openStream();
                JSONParser jsonParser = new JSONParser(stream, "UTF-8");
                ArrayList jsonObject = (ArrayList)jsonParser.parse();
                log.info("Get json: {}", jsonObject);
                if (jsonObject.size() > 1) {
                    LinkedHashMap obj2 = (LinkedHashMap)jsonObject.get(1);
                    ArrayList securities = (ArrayList)obj2.get("securities");
                    for (var ij = 0; ij < securities.size(); ij++) {
                        LinkedHashMap security = (LinkedHashMap)securities.get(ij);
                        if (!instrument.getMoexCode().equals(security.get("SHORTNAME"))) {
                            shortnames.add((String)security.get("SHORTNAME"));
                            continue;
                        }
                        var lastSettlePrice = security.get("LASTSETTLEPRICE");
                        BigDecimal price;
                        if (lastSettlePrice instanceof BigDecimal) {
                            price = (BigDecimal) lastSettlePrice;
                        } else if (lastSettlePrice instanceof BigInteger) {
                            price = new BigDecimal((BigInteger)lastSettlePrice);
                        } else {
                            log.error("Getting moex page contract result {} ({}) fail: LASTSETTLEPRICE = {} in unknown format", instrument.getFigi(), instrument.getMoexCode(), lastSettlePrice);
                            continue;
                        }
                        var imTime = (String)security.get("IMTIME");
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        TemporalAccessor acc = formatter.parse(imTime);
                        LocalDateTime.from(acc);
                        OffsetDateTime dateTime = LocalDateTime.from(acc).atOffset(ZoneId.systemDefault().getRules().getOffset(Instant.now()));
                        dateTime = dateTime.minusMinutes(dateTime.getMinute());
                        dateTime = dateTime.minusSeconds(dateTime.getSecond());
                        if (dateTime.getHour() != 19 && dateTime.getHour() != 14) {
                            log.error("Getting moex page contract result {} ({}) fail: hour in {} ({}) is not 19 or 14", instrument.getFigi(), instrument.getMoexCode(), imTime, dateTime);
                            continue;
                        }
                        contractResult = saveContractResult(instrument, dateTime, price);
                        break;
                    }
                }
                if (null == contractResult) {
                    log.error("Getting moex page contract result {} ({}) fail: no suitable code found among {}", instrument.getFigi(), instrument.getMoexCode(), String.join(",", shortnames));
                }
            } catch (Exception e) {
                var msg = String.format("An error during get moex page contract result from %s: %s", urlString, e.getMessage());
                log.error(msg, e);
            }
        }
    }

    private List<InstrumentService.Instrument> getActiveFutures() {
        var instruments = strategySelector.getInstrumentsForActiveStrategies();
        var futures = instruments.stream().filter(i -> i.getType() == InstrumentService.Type.future).collect(Collectors.toList());
        var orders = orderRepository.findByPurchaseDateTimeGreaterThan(OffsetDateTime.now().minusDays(14));
        orders.forEach(o -> {
            var instrument = instrumentService.getInstrument(o.getFigi());
            if (instrument.getType() == InstrumentService.Type.future) {
                if (futures.stream().filter(f -> f.getFigi().equals(o.getFigi())).findFirst().isEmpty()) {
                    futures.add(instrument);
                }
            }
        });
        return futures;
    }

    private InstrumentEntity getFutureInstrumentEntity(InstrumentService.Instrument future) {
        var instrument = instrumentRepository.findByFigi(future.getFigi());
        if (instrument == null) {
            String moexCode = null;
            var nameSplit = future.getName().split(" ", 2);
            if (nameSplit.length > 0) {
                moexCode = nameSplit[0];
            }
            instrument = InstrumentEntity.builder()
                    .figi(future.getFigi())
                    .name(future.getName())
                    .tiket(future.getTiket())
                    .currency(future.getCurrency())
                    .priceIncrement(future.getMinPriceIncrement())
                    .moexCode(moexCode)
                    .build();
            instrumentRepository.save(instrument);
        }
        return instrument;
    }

    private ContractResultEntity saveContractResult(InstrumentEntity instrument, OffsetDateTime dateTime, BigDecimal price) {
        log.info("Parsed moex contract result from {}: {} {}", instrument.getMoexCode(), dateTime, price);
        var contractResult = contractResultRepository.findByFigiAndDateTime(instrument.getFigi(), dateTime);
        if (null != contractResult) {
            if (contractResult.getSettlementPrice().compareTo(price) != 0) {
                log.info("Update moex contract result for {}: {} {} => {}", instrument.getMoexCode(), dateTime, contractResult.getSettlementPrice(), price);
                contractResult.setSettlementPrice(price);
                contractResult = contractResultRepository.save(contractResult);
            }
        } else {
            log.info("New moex contract result for {}: {} {}", instrument.getMoexCode(), dateTime, price);
            contractResult = ContractResultEntity.builder()
                    .figi(instrument.getFigi())
                    .dateTime(dateTime)
                    .settlementPrice(price)
                    .build();
            contractResult = contractResultRepository.save(contractResult);
        }
        return contractResult;
    }

    @PostConstruct
    void init() {
        getDerivativeUsdRates();
        getContractResults();
        getDayContractResults();
    }
}
