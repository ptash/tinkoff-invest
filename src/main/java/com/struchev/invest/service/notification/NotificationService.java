package com.struchev.invest.service.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.request.SendMessage;
import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.expression.Date;
import com.struchev.invest.service.processor.FactorialInstrumentByFiatService;
import com.struchev.invest.strategy.AStrategy;
import io.micrometer.core.instrument.util.StringUtils;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service to send messages and errors in preconfigured channels
 * <p>
 * Telegram channel configuration
 * - telegram.bot.token
 * - telegram.bot.chat-id
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService implements INotificationService{

    @Value("${telegram.bot.token:}")
    private String telegramBotToken;
    @Value("${telegram.bot.chat-id:}")
    private String telegramBotChatId;
    private String lastMessage;

    @Value("${logging.file.path}")
    private String loggingPath;

    private TelegramBot bot;

    private Map<String, Marker> reportStrategyLoggerMap = new HashMap<>();
    private Map<String, Marker> reportOfferLoggerMap = new HashMap<>();
    private OffsetDateTime dateTime = OffsetDateTime.now();

    public void sendMessage(String content) {
        if (bot != null && StringUtils.isNotEmpty(telegramBotChatId)
                && (this.lastMessage == null || !this.lastMessage.equals(content))
        ) {
            var message = new SendMessage(telegramBotChatId, content);
            this.bot.execute(message);
            this.lastMessage = content;
        }
    }

    public void sendMessageAndLog(String content) {
        log.warn(content);
        sendMessage(content);
    }

    public void sendBuyInfo(AStrategy strategy, OrderDomainEntity order, CandleDomainEntity candle) {
        log.info(
                getOfferReportLogMarker(strategy, candle.getFigi()),
                "{} | B | {}",
                formatDateTime(order.getPurchaseDateTime()),
                order.getPurchasePrice()
        );
        var msg = String.format("Buy %s (%s), %s, %s, %s. Wanted %s", order.getFigi(), order.getFigiTitle(),
                order.getPurchasePrice(), order.getPurchaseDateTime(), order.getStrategy(), candle.getClosingPrice());
        this.sendMessageAndLog(msg);
    }

    public void sendSellInfo(AStrategy strategy, OrderDomainEntity order, CandleDomainEntity candle) {
        log.info(
                getOfferReportLogMarker(strategy, candle.getFigi()),
                "{} | S | {}",
                formatDateTime(order.getSellDateTime()),
                order.getSellPrice(),
                order.getSellPrice()
        );
        var msg = String.format("Sell %s (%s), %s (%s), %s, %s. Wanted: %s", candle.getFigi(), order.getFigiTitle(),
                order.getSellPrice(), order.getSellProfit(), order.getSellDateTime(), order.getStrategy(), candle.getClosingPrice());
        this.sendMessageAndLog(msg);
    }

    public void sendSellLimitInfo(AStrategy strategy, OrderDomainEntity order, CandleDomainEntity candle) {
        if (order.getSellLimitOrderId() == null) {
            return;
        }
        if (order.getCellLots() == null || order.getCellLots() != order.getLots()) {
            var msg = String.format("Bid limit open %s (%s), %s (%s) %s. Wanted: %s", candle.getFigi(), order.getFigiTitle(),
                    order.getSellLimitOrderId(), order.getCellLots(), order.getStrategy(), order.getSellPriceLimitWanted());
            this.sendMessageAndLog(msg);
            return;
        }
        log.info(
                getOfferReportLogMarker(strategy, candle.getFigi()),
                "{} | S | {}",
                formatDateTime(order.getSellDateTime()),
                order.getSellPrice(),
                order.getSellPrice()
        );
        var msg = String.format("Sell bid limit success %s (%s), %s (%s), %s, %s. Wanted: %s", candle.getFigi(), order.getFigiTitle(),
                order.getSellPrice(), order.getSellProfit(), order.getSellDateTime(), order.getStrategy(), order.getSellPriceLimitWanted());
        this.sendMessageAndLog(msg);
    }

    public String formatDateTime(OffsetDateTime date) {
        return Date.formatDateTimeWithTimeZone(date);
    }

    @PostConstruct
    private void init() {
        // отправляем сообщение в telegram в случае level ERROR в логах
        var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        var appender = new UnsynchronizedAppenderBase() {
            @Override
            protected void append(Object eventObject) {
                if (eventObject instanceof LoggingEvent) {
                    var event = (LoggingEvent) eventObject;
                    if (event.getLevel() == Level.ERROR) {
                        sendMessage(event.toString());
                    }
                }
            }
        };
        loggerContext.getLoggerList().forEach(l -> l.addAppender(appender));
        appender.start();

        // по умолчанию на любое сообщение ответим в телеграм чат отправив chatId
        if (StringUtils.isNotEmpty(telegramBotToken)) {
            this.bot = new TelegramBot(telegramBotToken);
            this.bot.setUpdatesListener(updates -> {
                updates.stream().forEach(update -> {
                    var chatId = update.message().chat().id();
                    var messageIn = update.message().text();
                    if (messageIn != null) {
                        var messageOut = String.format("Received from chat id %s: %s", chatId, messageIn);
                        bot.execute(new SendMessage(update.message().chat().id(), messageOut));
                    }
                });
                return UpdatesListener.CONFIRMED_UPDATES_ALL;
            });
        }

        if (StringUtils.isEmpty(telegramBotToken) || StringUtils.isEmpty(telegramBotChatId)) {
            log.warn("Telegram properties no defined properly: telegram.bot.token: , telegram.bot.chat-id: {}",
                    telegramBotToken, telegramBotChatId);
        }
    }

    private void buildDygraphsPage(String instanceName, String headerLine)
    {
        String fileName = loggingPath + "/" + instanceName + "Dygraphs-" + dateTime.toString().replace(":", "_") + ".html";
        File f = new File(fileName);
        File fTemplate = new File("./dygraphs/dygraphs.html");
        if (f.exists() || !fTemplate.exists()) {
            return;
        }
        try {
            Path filePath = fTemplate.toPath();
            String content = Files.readString(filePath);
            var visibility = "true";
            List<String> items = Lists.newArrayList(Splitter.on("|").split(headerLine));
            for (var i = 1; i < items.size(); i++) {
                if (items.get(i).equals("strategy")) {
                    visibility += ",false";
                }
                visibility += ",true";
            }
            content = content.replace("'%visibility%'", visibility);
            content = content.replace("s.csv", instanceName + "Strategy-" + dateTime.toString().replace(":", "_") + ".csv");
            content = content.replace("a.csv",  instanceName + "Offer-" + dateTime.toString().replace(":", "_") + ".csv");

            BufferedWriter bufferedWriter = Files.newBufferedWriter(Paths.get(fileName));
            bufferedWriter.write(content); // to write some data
            bufferedWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private Marker getOfferReportLogMarker(AStrategy strategy, String figi)
    {
        String instanceName = strategy.getName() + figi;
        synchronized (reportOfferLoggerMap) {
            if (!reportOfferLoggerMap.containsKey(instanceName)) {
                reportOfferLoggerMap.put(instanceName, this.createReportLogMarker(
                        instanceName + "Offer",
                        "Date|Short text|Text"
                ));
            }
        }
        return reportOfferLoggerMap.get(instanceName);
    }

    public void reportStrategy(AStrategy strategy, String figi, String headerLine, String format, Object... arguments)
    {
        log.info(getStrategyReportLogMarker(strategy, figi, headerLine), format, arguments);
    }

    @Builder
    @Data
    public static class ReportData {
        CandleDomainEntity candle;
        String headerLine;
        String format;
        Object[] arguments;
    }

    private Map<String, ReportData> reportDataMap = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 1000;
        }
    };

    private synchronized void addReportData(String indent, ReportData v)
    {
        reportDataMap.put(indent, v);
    }

    private synchronized ReportData getReportData(String indent)
    {
        if (reportDataMap.containsKey(indent)) {
            return reportDataMap.get(indent);
        }
        return null;
    }

    public void reportStrategyExt(Boolean res, AStrategy strategy, CandleDomainEntity candle, String headerLine, String format, Object... arguments) {
        var key = strategy.getName() + candle.getFigi();
        var reportData = getReportData(key);
        if (
            null != reportData
            && !formatDateTime(reportData.getCandle().getDateTime()).equals(formatDateTime(candle.getDateTime()))
        ) {
            log.info(getStrategyReportLogMarker(strategy, candle.getFigi(), reportData.getHeaderLine()), reportData.getFormat(), reportData.getArguments());
        }
        if (res) {
            log.info(getStrategyReportLogMarker(strategy, candle.getFigi(), headerLine), format, arguments);
        }
        addReportData(key, ReportData.builder()
            .candle(candle)
            .headerLine(headerLine)
            .format(format)
            .arguments(arguments)
            .build());
    }

    private Marker getStrategyReportLogMarker(AStrategy strategy, String figi, String headerLine)
    {
        String instanceName = strategy.getName() + figi;
        synchronized (reportStrategyLoggerMap) {
            if (!reportStrategyLoggerMap.containsKey(instanceName)) {
                reportStrategyLoggerMap.put(instanceName, this.createReportLogMarker(
                        instanceName + "Strategy",
                        headerLine
                ));
                buildDygraphsPage(instanceName, headerLine);
            }
        }
        return reportStrategyLoggerMap.get(instanceName);
    }

    private Marker createReportLogMarker(String instanceName, String headerLine) {
        Marker marker = MarkerFactory.getMarker(instanceName);
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        String fileName = loggingPath + "/" + instanceName + "-" + dateTime.toString().replace(":", "_") + ".csv";
        log.info("Create log file {}", fileName);
        FileAppender fileAppender = new FileAppender();
        fileAppender.setContext(loggerContext);
        fileAppender.setName(instanceName);
        fileAppender.setFile(fileName);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%m%n");
        encoder.start();

        Filter filter = new Filter() {
            @Override
            public FilterReply decide(Object obj) {
                if (!isStarted()) {
                    return FilterReply.NEUTRAL;
                }
                LoggingEvent event = (LoggingEvent) obj;
                if (event.getMarker().getName().equals(instanceName)) {
                    return FilterReply.NEUTRAL;
                } else {
                    return FilterReply.DENY;
                }
            }

        };
        filter.start();
        fileAppender.addFilter(filter);
        fileAppender.setEncoder(encoder);

        fileAppender.start();

        Logger logger = (Logger) log;
        logger.addAppender(fileAppender);
        log.info(marker, headerLine);
        return marker;
    }
}
