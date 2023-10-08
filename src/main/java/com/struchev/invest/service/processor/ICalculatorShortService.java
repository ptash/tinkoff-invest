package com.struchev.invest.service.processor;

import com.struchev.invest.service.candle.CandleHistoryReverseForShortService;
import com.struchev.invest.service.candle.ICandleHistoryService;
import com.struchev.invest.service.notification.INotificationService;
import com.struchev.invest.service.notification.NotificationForShortService;

interface ICalculatorShortService {
    ICalculatorShortService cloneService() throws CloneNotSupportedException;
    void setCandleHistoryService(ICandleHistoryService candleHistoryService);
    void setNotificationService(INotificationService notificationForShortService);
}
