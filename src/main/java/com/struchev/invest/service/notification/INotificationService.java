package com.struchev.invest.service.notification;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.strategy.AStrategy;

import java.time.OffsetDateTime;

public interface INotificationService {
    void reportStrategyExt(Boolean res, AStrategy strategy, CandleDomainEntity candle, String headerLine, String format, Object... arguments);
    String formatDateTime(OffsetDateTime date);
}
