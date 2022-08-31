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
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.request.SendMessage;
import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.strategy.instrument_by_fiat_cross.AInstrumentByFiatCrossStrategy;
import io.micrometer.core.instrument.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

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
public class NotificationService {

    @Value("${telegram.bot.token:}")
    private String telegramBotToken;
    @Value("${telegram.bot.chat-id:}")
    private String telegramBotChatId;

    private TelegramBot bot;

    private Map<String, Marker> reportStrategyLoggerMap = new HashMap<>();
    private Map<String, Marker> reportOfferLoggerMap = new HashMap<>();

    public void sendMessage(String content) {
        if (bot != null && StringUtils.isNotEmpty(telegramBotChatId)) {
            var message = new SendMessage(telegramBotChatId, content);
            this.bot.execute(message);
        }
    }

    public void sendMessageAndLog(String content) {
        log.warn(content);
        sendMessage(content);
    }

    public void sendBuyInfo(OrderDomainEntity order, CandleDomainEntity candle) {
        log.info(
                getOfferReportLogMarker(candle.getFigi()),
                "{} | B | Bye by {}",
                order.getPurchaseDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE) + " " + order.getPurchaseDateTime().format(DateTimeFormatter.ISO_LOCAL_TIME),
                order.getPurchasePrice()
        );
        var msg = String.format("Buy %s (%s), %s, %s, %s. Wanted %s", order.getFigi(), order.getFigiTitle(),
                order.getPurchasePrice(), order.getPurchaseDateTime(), order.getStrategy(), candle.getClosingPrice());
        this.sendMessageAndLog(msg);
    }

    public void sendSellInfo(OrderDomainEntity order, CandleDomainEntity candle) {
        log.info(
                getOfferReportLogMarker(candle.getFigi()),
                "{} | S | Sell by {}",
                order.getSellDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE) + " " + order.getSellDateTime().format(DateTimeFormatter.ISO_LOCAL_TIME),
                order.getSellPrice()
        );
        var msg = String.format("Sell %s (%s), %s (%s), %s, %s. Wanted: %s", candle.getFigi(), order.getFigiTitle(),
                order.getSellPrice(), order.getSellProfit(), order.getSellDateTime(), order.getStrategy(), candle.getClosingPrice());
        this.sendMessageAndLog(msg);
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

    private Marker getOfferReportLogMarker(String figi)
    {
        String instanceName = figi;
        if (!reportStrategyLoggerMap.containsKey(instanceName)) {
            reportStrategyLoggerMap.put(instanceName, this.createReportLogMarker(
                    "Offer" + instanceName,
                    "Date|Short text|Text"
            ));
        }
        return reportStrategyLoggerMap.get(instanceName);
    }

    public void reportStrategy(AInstrumentByFiatCrossStrategy strategy, String figi, String headerLine, String format, Object... arguments)
    {
        log.info(getStrategyReportLogMarker(strategy, figi, headerLine), format, arguments);
    }

    private Marker getStrategyReportLogMarker(AInstrumentByFiatCrossStrategy strategy, String figi, String headerLine)
    {
        String instanceName = strategy.getName() + figi;
        if (!reportStrategyLoggerMap.containsKey(instanceName)) {
            reportStrategyLoggerMap.put(instanceName, this.createReportLogMarker(
                    "Strategy" + instanceName,
                    headerLine
            ));
        }
        return reportStrategyLoggerMap.get(instanceName);
    }

    private Marker createReportLogMarker(String instanceName, String headerLine) {
        Marker marker = MarkerFactory.getMarker(instanceName);
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        String fileName = "./logs" + "/" + instanceName + "-" + OffsetDateTime.now().toString() + ".csv";
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
