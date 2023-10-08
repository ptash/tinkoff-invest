package com.struchev.invest.service.candle;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.repository.CandleRepository;
import com.struchev.invest.service.tinkoff.ITinkoffCommonAPI;
import com.struchev.invest.strategy.AStrategy;
import com.struchev.invest.strategy.StrategySelector;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tinkoff.piapi.contract.v1.Candle;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Service to load and store actual history of candles
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CandleHistoryReverseForShortService implements ICandleHistoryService {
    private final CandleHistoryService candleHistoryService;

    @Value("${candle.listener.enabled}")
    Boolean isCandleListenerEnabled;

    public List<CandleDomainEntity> getCandlesByFigiBetweenDateTimes(String figi, OffsetDateTime startDateTime, OffsetDateTime endDateTime, String interval) {
        var res = candleHistoryService.getCandlesByFigiBetweenDateTimes(figi, startDateTime, endDateTime, interval);
        return prepareForShort(res);
    }

    public List<CandleDomainEntity> getCandlesByFigiByLength(String figi, OffsetDateTime currentDateTime, Integer length, String interval)
    {
        var res = candleHistoryService.getCandlesByFigiByLength(figi, currentDateTime, length, interval);
        return prepareForShort(res);
    }

    private List<CandleDomainEntity> prepareForShort(List<CandleDomainEntity> list)
    {
        if (!isCandleListenerEnabled) {
            return list.stream().map(cOrig -> {
                var c = cOrig.clone();
                prepareCandleForShort(c);
                return c;
            }).collect(Collectors.toList());
        }
        for (var i = list.listIterator(); i.hasNext();) {
            var c = i.next();
            prepareCandleForShort(c);
        };
        return list;
    }
    public CandleDomainEntity prepareCandleForShort(CandleDomainEntity c)
    {
        c.setClosingPrice(c.getClosingPrice().multiply(BigDecimal.valueOf(-1)));
        c.setOpenPrice(c.getOpenPrice().multiply(BigDecimal.valueOf(-1)));
        c.setHighestPrice(c.getHighestPrice().multiply(BigDecimal.valueOf(-1)));
        c.setLowestPrice(c.getLowestPrice().multiply(BigDecimal.valueOf(-1)));
        return c;
    }
}
