package com.struchev.invest.service.tinkoff;

import com.struchev.invest.service.dictionary.InstrumentService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

public interface ITinkoffOrderAPI {
    @AllArgsConstructor
    @Data
    @Builder
    class OrderResult {
        String orderId;
        String orderUuid;
        BigDecimal commissionInitial;
        BigDecimal commission;
        BigDecimal price;
        BigDecimal orderPrice;
        Long lots;
        Boolean active;
        Exception exception;
    }

    OrderResult buy(InstrumentService.Instrument instrument, BigDecimal price, Integer count);

    OrderResult sell(InstrumentService.Instrument instrument, BigDecimal price, Integer count);

    public OrderResult sellLimit(InstrumentService.Instrument instrument, BigDecimal price, Integer count, String uuid, String orderId);

    public OrderResult closeSellLimit(InstrumentService.Instrument instrument, String orderId);

    public Boolean checkGoodSell(InstrumentService.Instrument instrument, BigDecimal price, Integer count, BigDecimal priceError);

    public Boolean checkGoodBuy(InstrumentService.Instrument instrument, BigDecimal price, Integer count, BigDecimal priceError);
}
