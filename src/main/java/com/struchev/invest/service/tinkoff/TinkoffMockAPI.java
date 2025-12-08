package com.struchev.invest.service.tinkoff;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.service.dictionary.InstrumentService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tinkoff.emulator", havingValue = "true", matchIfMissing = true)
public class TinkoffMockAPI extends ATinkoffAPI {

    private final BigDecimal PERCENT_SHARE = new BigDecimal("0.0004");
    private final BigDecimal PERCENT_FUTURE = new BigDecimal("0.00025");

    @Override
    public OrderResult buy(InstrumentService.Instrument instrument, BigDecimal price, Integer count) {
        return OrderResult.builder()
                .commissionInitial(calculateCommission(price, count, instrument))
                .commission(calculateCommission(price, count, instrument))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
    }

    @Override
    public OrderResult buyShort(InstrumentService.Instrument instrument, BigDecimal price, Integer count) {
        return OrderResult.builder()
                .commissionInitial(calculateCommission(price, count, instrument))
                .commission(calculateCommission(price, count, instrument))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
    }

    @Override
    public OrderResult sell(InstrumentService.Instrument instrument, BigDecimal price, Integer count) {
        return OrderResult.builder()
                .commissionInitial(calculateCommission(price, count, instrument))
                .commission(calculateCommission(price, count, instrument))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
    }

    @Override
    public OrderResult sellShort(InstrumentService.Instrument instrument, BigDecimal price, Integer count) {
        return OrderResult.builder()
                .commissionInitial(calculateCommission(price, count, instrument))
                .commission(calculateCommission(price, count, instrument))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
    }

    public OrderResult sellLimit(InstrumentService.Instrument instrument, BigDecimal price, Integer count, String uuid, String orderId, CandleDomainEntity candle) {
        log.info("sellLimit: Sell limit for {} with price {} and limit {}", instrument.getFigi(), candle.getHighestPrice(), price);
        if (candle.getHighestPrice().compareTo(price) >= 0) {
            log.info("sellLimit: OK");
            return OrderResult.builder()
                    .orderUuid(UUID.randomUUID().toString())
                    .orderId(UUID.randomUUID().toString())
                    .commission(calculateCommission(price, count, instrument))
                    .lots(count.longValue())
                    .orderPrice(price.multiply(BigDecimal.valueOf(count)))
                    .price(price)
                    .pricePt(price)
                    .isExecuted(true)
                    .build();
        }
        return OrderResult.builder().build();
    }

    @Builder
    @Data
    public static class OrderLimit {
        CandleDomainEntity orderId;
        BigDecimal price;
        OrderResult orderResult;
    }

    private Map<String, OrderResult> orderLimitArray = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 50;
        }
    };

    private synchronized void addOrderResult(InstrumentService.Instrument instrument, OrderResult order)
    {
        var indent = instrument.getFigi();
        orderLimitArray.put(indent, order);
    }

    private synchronized OrderResult getOrderResult(String figi)
    {
        if (orderLimitArray.containsKey(figi)) {
            return orderLimitArray.get(figi);
        }
        return null;
    }

    public OrderResult sellLimitShort(InstrumentService.Instrument instrument, BigDecimal price, Integer count, String uuid, String orderId, CandleDomainEntity candle) {
        log.info("sellLimitShort: Sell limit for {} with price {} and limit {} date {}", instrument.getFigi(), candle.getLowestPrice(), price, candle.getDateTime());
        var order = OrderResult.builder()
                .orderUuid(UUID.randomUUID().toString())
                .orderId(UUID.randomUUID().toString())
                .commission(calculateCommission(price, count, instrument))
                .lots(count.longValue())
                .orderPrice(price.multiply(BigDecimal.valueOf(count)))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
        if (candle.getLowestPrice().compareTo(price) <= 0) {
            return order;
        } else {
            addOrderResult(instrument, order);
        }
        return OrderResult.builder().build();
    }

    public OrderResult closeSellLimit(InstrumentService.Instrument instrument, String orderId, CandleDomainEntity candle) {
        var order = getOrderResult(candle.getFigi());
        if (order != null) {
            var price = order.getPrice();
            log.info("sellLimitShort: Sell limit for {} with price {} and limit {} date {}", instrument.getFigi(), candle.getLowestPrice(), price, candle.getDateTime());
            if (candle.getLowestPrice().compareTo(price) <= 0) {
                return order;
            }
        }
        return OrderResult.builder()
                .isExecuted(false)
                .build();
    }

    public OrderResult closeAllSellLimit(InstrumentService.Instrument instrument, CandleDomainEntity candle) {
        return OrderResult.builder()
                .isExecuted(false)
                .build();
    }

    @Override
    public Boolean checkGoodSell(InstrumentService.Instrument instrument, BigDecimal price, Integer count, BigDecimal priceError, CandleDomainEntity candle) {
        //var delta = price.multiply(priceError);
        //delta = moneyRound(instrument, delta);
        if (candle.getHighestPrice().compareTo(price) >= 0) {
            return true;
        } else {
            log.info("checkGoodSell: false = {} >= {}", candle.getHighestPrice(), price);
            return false;
        }
    }

    @Override
    public Boolean checkGoodBuy(InstrumentService.Instrument instrument, BigDecimal price, Integer count, BigDecimal priceError, CandleDomainEntity candle) {
        //var delta = price.multiply(priceError);
        //delta = moneyRound(instrument, delta);
        return candle.getLowestPrice().compareTo(price) <= 0;
    }

    private BigDecimal moneyRound(InstrumentService.Instrument instrument, BigDecimal price) {
        return price.divide(instrument.getMinPriceIncrement(), 0, RoundingMode.HALF_UP).multiply(instrument.getMinPriceIncrement());
    }

    private BigDecimal calculateCommission(BigDecimal price, Integer count, InstrumentService.Instrument instrument) {
        var percent = PERCENT_SHARE;
        if (instrument.getType() == InstrumentService.Type.future) {
            percent = PERCENT_FUTURE;
        }
        var commission = price.multiply(percent).multiply(BigDecimal.valueOf(count));
        //var commission = price.multiply(PERCENT).multiply(BigDecimal.valueOf(count)).setScale(2, RoundingMode.UP);
        return commission;
    }

}
