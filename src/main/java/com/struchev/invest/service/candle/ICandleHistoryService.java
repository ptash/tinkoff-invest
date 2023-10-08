package com.struchev.invest.service.candle;

import com.struchev.invest.entity.CandleDomainEntity;

import java.time.OffsetDateTime;
import java.util.List;

public interface ICandleHistoryService {
    List<CandleDomainEntity> getCandlesByFigiBetweenDateTimes(String figi, OffsetDateTime startDateTime, OffsetDateTime endDateTime, String interval);
    List<CandleDomainEntity> getCandlesByFigiByLength(String figi, OffsetDateTime currentDateTime, Integer length, String interval);
}
