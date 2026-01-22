package com.struchev.invest.service.tinkoff;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.entity.OrderDomainEntity;
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
    public OrderResult buy(InstrumentService.Instrument instrument, BigDecimal price, Integer count, CandleDomainEntity candle) {
        if (candle.getHighestPrice().compareTo(price) < 0) {
            price = candle.getHighestPrice();
        }
        return OrderResult.builder()
                .commissionInitial(calculateCommission(price, count, instrument))
                .commission(calculateCommission(price, count, instrument))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
    }

    @Override
    public OrderResult buyShort(InstrumentService.Instrument instrument, BigDecimal price, Integer count, CandleDomainEntity candle) {
        if (candle.getLowestPrice().compareTo(price) > 0) {
            price = candle.getLowestPrice();
        }
        return OrderResult.builder()
                .commissionInitial(calculateCommission(price, count, instrument))
                .commission(calculateCommission(price, count, instrument))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
    }

    @Override
    public OrderResult sell(InstrumentService.Instrument instrument, BigDecimal price, Integer count, CandleDomainEntity candle) {
        price = price.max(candle.getLowestPrice());
        return OrderResult.builder()
                .commissionInitial(calculateCommission(price, count, instrument))
                .commission(calculateCommission(price, count, instrument))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
    }

    @Override
    public OrderResult sellShort(InstrumentService.Instrument instrument, BigDecimal price, Integer count, CandleDomainEntity candle) {
        price = price.min(candle.getHighestPrice());
        return OrderResult.builder()
                .commissionInitial(calculateCommission(price, count, instrument))
                .commission(calculateCommission(price, count, instrument))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
    }

    public OrderResult sellLimit(InstrumentService.Instrument instrument, BigDecimal price, Integer count, String uuid, String orderId, CandleDomainEntity candle, OrderDomainEntity order) {
        log.info("sellLimit: Sell limit for {} with price {} and limit {}", instrument.getFigi(), candle.getHighestPrice(), price);
        var limitOrder = OrderResult.builder()
                .orderUuid(UUID.randomUUID().toString())
                .orderId(UUID.randomUUID().toString())
                .commission(calculateCommission(price, count, instrument))
                .lots(count.longValue())
                .orderPrice(price.multiply(BigDecimal.valueOf(count)))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
        if (
                (candle.getHighestPrice().compareTo(price) > 0 && !candle.getDateTime().equals(order.getPurchaseDateTime()))
                || (candle.getClosingPrice().compareTo(price) > 0 && candle.getDateTime().equals(order.getPurchaseDateTime()))
        ) {
            if (candle.getLowestPrice().compareTo(price) > 0) {
                limitOrder.setPrice(candle.getLowestPrice());
                limitOrder.setPricePt(candle.getLowestPrice());
                limitOrder.setCommission(calculateCommission(candle.getLowestPrice(), count, instrument));
            }
            log.info("sellLimit: OK");
            return limitOrder;
        } else {
            addOrderResult(instrument, limitOrder);
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
            return size() > 100;
        }
    };

    private synchronized void addOrderResult(InstrumentService.Instrument instrument, OrderResult order)
    {
        var indent = instrument.getFigi() + order.getOrderId();
        orderLimitArray.put(indent, order);
    }

    private synchronized OrderResult getOrderResult(String figi, String orderId)
    {
        var indent = figi + orderId;
        if (orderLimitArray.containsKey(indent)) {
            return orderLimitArray.get(indent);
        }
        return null;
    }

    public OrderResult sellLimitShort(InstrumentService.Instrument instrument, BigDecimal price, Integer count, String uuid, String orderId, CandleDomainEntity candle, OrderDomainEntity order) {
        log.info("sellLimitShort: Sell limit for {} with price {} and limit {} date {}", instrument.getFigi(), candle.getLowestPrice(), price, candle.getDateTime());
        var limitOrder = OrderResult.builder()
                .orderUuid(UUID.randomUUID().toString())
                .orderId(UUID.randomUUID().toString())
                .commission(calculateCommission(price, count, instrument))
                .lots(count.longValue())
                .orderPrice(price.multiply(BigDecimal.valueOf(count)))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
        if (
                (candle.getLowestPrice().compareTo(price) < 0 && !candle.getDateTime().equals(order.getSellDateTime()))
                || (candle.getClosingPrice().compareTo(price) < 0 && candle.getDateTime().equals(order.getSellDateTime()))
        ) {
            if (candle.getHighestPrice().compareTo(price) < 0) {
                limitOrder.setPrice(candle.getHighestPrice());
                limitOrder.setPricePt(candle.getHighestPrice());
                limitOrder.setCommission(calculateCommission(candle.getHighestPrice(), count, instrument));
            }
            return limitOrder;
        } else {
            addOrderResult(instrument, limitOrder);
        }
        return OrderResult.builder().build();
    }

    public OrderResult closeSellLimit(InstrumentService.Instrument instrument, String orderId, CandleDomainEntity candle) {
        var order = getOrderResult(candle.getFigi(), orderId);
        if (order != null) {
            var price = order.getPrice();
            log.info("closeSellLimit: Sell limit for {} with price {} and limit {} date {}", instrument.getFigi(), candle.getLowestPrice(), price, candle.getDateTime());
            if (candle.getLowestPrice().compareTo(price) < 0) {
                if (candle.getHighestPrice().compareTo(price) < 0) {
                    order.setPrice(candle.getHighestPrice());
                    order.setPricePt(candle.getHighestPrice());
                    order.setCommission(calculateCommission(candle.getHighestPrice(), Math.toIntExact(order.getLots()), instrument));
                }
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
        //return candle.getLowestPrice().compareTo(price) <= 0;
        return price.compareTo(candle.getLowestPrice()) >= 0;
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
