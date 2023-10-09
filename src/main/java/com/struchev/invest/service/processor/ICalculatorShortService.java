package com.struchev.invest.service.processor;

import com.struchev.invest.service.candle.CandleHistoryReverseForShortService;
import com.struchev.invest.service.candle.ICandleHistoryService;
import com.struchev.invest.service.notification.INotificationService;
import com.struchev.invest.service.notification.NotificationForShortService;
import com.struchev.invest.service.order.IOrderService;

interface ICalculatorShortService {
    ICalculatorShortService cloneService(IOrderService orderService) throws CloneNotSupportedException;
    void setCandleHistoryService(ICandleHistoryService candleHistoryService);
    void setNotificationService(INotificationService notificationForShortService);

    void setOrderService(IOrderService orderService);
}
