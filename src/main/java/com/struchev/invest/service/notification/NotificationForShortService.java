package com.struchev.invest.service.notification;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.strategy.AStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationForShortService implements INotificationService
{
    private final NotificationService notificationService;
    Map<String, AStrategy> strategyMap = new HashMap<>();
    @Override
    public void reportStrategyExt(Boolean res, AStrategy strategy, CandleDomainEntity candle, String headerLine, String format, Object... arguments) {
        if (!strategyMap.containsKey(strategy.getName())) {
            strategyMap.put(strategy.getName(), new StrategyShort(strategy.getName() + "Short"));
        }
        notificationService.reportStrategyExt(res, strategyMap.get(strategy.getName()), candle, headerLine, format, arguments);
    }

    @Override
    public String formatDateTime(OffsetDateTime date) {
        return notificationService.formatDateTime(date);
    }
}
