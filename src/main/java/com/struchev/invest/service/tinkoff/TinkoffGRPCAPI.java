package com.struchev.invest.service.tinkoff;

import com.struchev.invest.service.dictionary.InstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@ConditionalOnProperty(name = "tinkoff.emulator", havingValue = "false")
@RequiredArgsConstructor
public class TinkoffGRPCAPI extends ATinkoffAPI {

    public OrderResult buy(InstrumentService.Instrument instrument, BigDecimal price, Integer count) {
        long quantity = count / instrument.getLot();
        var quotation = Quotation.newBuilder()
                .setUnits(price.longValue())
                .setNano(price.remainder(BigDecimal.ONE).movePointRight(9).intValue())
                .build();
        var uuid = UUID.randomUUID().toString();
        log.info("Send postOrderSync with: figi {}, quantity {}, quotation {}, direction {}, acc {}, type {}, id {}",
                instrument.getFigi(), quantity, quotation, OrderDirection.ORDER_DIRECTION_BUY, getAccountId(), OrderType.ORDER_TYPE_MARKET, uuid);

        if (getIsSandboxMode()) {
            var result = getApi().getSandboxService().postOrderSync(instrument.getFigi(), quantity, quotation,
                    OrderDirection.ORDER_DIRECTION_BUY, getAccountId(), OrderType.ORDER_TYPE_MARKET, uuid);
            return OrderResult.builder()
                    .orderId(result.getOrderId())
                    .commissionInitial(toBigDecimal(result.getInitialCommission(), 8))
                    .commission(toBigDecimal(result.getInitialCommission(), 8))
                    .price(toBigDecimal(result.getExecutedOrderPrice(), 8, price))
                    .build();
        } else {
            var result = getApi().getOrdersService().postOrderSync(instrument.getFigi(), quantity, quotation,
                    OrderDirection.ORDER_DIRECTION_BUY, getAccountId(), OrderType.ORDER_TYPE_MARKET, uuid);
            return OrderResult.builder()
                    .orderId(result.getOrderId())
                    .commissionInitial(toBigDecimal(result.getInitialCommission(), 8))
                    .commission(getExecutedCommission(result))
                    .price(toBigDecimal(result.getExecutedOrderPrice(), 8, price))
                    .build();
        }
    }

    public OrderResult sell(InstrumentService.Instrument instrument, BigDecimal price, Integer count) {
        long quantity = count / instrument.getLot();
        var quotation = Quotation.newBuilder()
                .setUnits(price.longValue())
                .setNano(price.remainder(BigDecimal.ONE).movePointRight(9).intValue())
                .build();
        var uuid = UUID.randomUUID().toString();
        log.info("Send postOrderSync with: figi {}, quantity {}, quotation {}, direction {}, acc {}, type {}, id {}",
                instrument.getFigi(), quantity, quotation, OrderDirection.ORDER_DIRECTION_SELL, getAccountId(), OrderType.ORDER_TYPE_MARKET, uuid);

        if (getIsSandboxMode()) {
            var result = getApi().getSandboxService().postOrderSync(instrument.getFigi(), quantity, quotation,
                    OrderDirection.ORDER_DIRECTION_SELL, getAccountId(), OrderType.ORDER_TYPE_MARKET, uuid);
            result.getOrderId();
            return OrderResult.builder()
                    .orderId(result.getOrderId())
                    .commissionInitial(toBigDecimal(result.getInitialCommission(), 8))
                    .commission(toBigDecimal(result.getInitialCommission(), 8))
                    .price(toBigDecimal(result.getExecutedOrderPrice(), 8, price))
                    .build();
        } else {
            var result = getApi().getOrdersService().postOrderSync(instrument.getFigi(), quantity, quotation,
                    OrderDirection.ORDER_DIRECTION_SELL, getAccountId(), OrderType.ORDER_TYPE_MARKET, uuid);
            return OrderResult.builder()
                    .orderId(result.getOrderId())
                    .commissionInitial(toBigDecimal(result.getInitialCommission(), 8))
                    .commission(getExecutedCommission(result))
                    .price(toBigDecimal(result.getExecutedOrderPrice(), 8, price))
                    .build();
        }
    }

    public Boolean checkGoodSell(InstrumentService.Instrument instrument, BigDecimal price, Integer count, BigDecimal priceError) {
        if (getIsSandboxMode()) {
            return true;
        }
        if (priceError.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        var orderBook = getApi().getMarketDataService().getOrderBookSync(instrument.getFigi(), 1);
        var askPrice = toBigDecimal(orderBook.getAsks(0).getPrice(), 8);
        var bidPrice = toBigDecimal(orderBook.getBids(0).getPrice(), 8);
        log.info("Order book sell {}: bid = {} ask = {} response = {}", price, bidPrice, askPrice, orderBook);
        var currentDelta = askPrice.subtract(bidPrice).divide(price, 8, RoundingMode.HALF_UP);
        if (currentDelta.compareTo(priceError) > 0) {
            log.info("Sell " + instrument.getFigi() + " error: the ask price " + askPrice + " differs from the bid " + bidPrice + " price by more than " + priceError + " < " + currentDelta);
            return false;
        }
        if (price.compareTo(bidPrice) > 0) {
            log.info("Sell " + instrument.getFigi() + " error: the bid price " + bidPrice + " price by less than wanted price " + price);
            return false;
        }
        return true;
    }

    public Boolean checkGoodBuy(InstrumentService.Instrument instrument, BigDecimal price, Integer count, BigDecimal priceError) {
        if (getIsSandboxMode()) {
            return true;
        }
        if (priceError.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        var orderBook = getApi().getMarketDataService().getOrderBookSync(instrument.getFigi(), 1);
        var askPrice = toBigDecimal(orderBook.getAsks(0).getPrice(), 8);
        var bidPrice = toBigDecimal(orderBook.getBids(0).getPrice(), 8);
        log.info("Order book buy {}: bid = {} ask = {} response = {}", price, bidPrice, askPrice, orderBook);
        var currentDelta = askPrice.subtract(bidPrice).divide(price, 8, RoundingMode.HALF_UP);
        if (currentDelta.compareTo(priceError) > 0) {
            log.info("Buy " + instrument.getFigi() + " error: the ask price " + askPrice + " differs from the bid " + bidPrice + " price by more than " + priceError + " < " + currentDelta);
            return false;
        }
        if (price.compareTo(askPrice) < 0) {
            log.info("Buy " + instrument.getFigi() + " error: the ask price " + askPrice + " price by more than wanted price " + price);
            return false;
        }
        return true;
    }

    private Boolean isZero(MoneyValue money) {
        return money.getNano() == 0 && money.getUnits() == 0;
    }

    private BigDecimal getExecutedCommission(PostOrderResponse postOrderResponse) {
        if (postOrderResponse.hasExecutedCommission()) {
            if (!isZero(postOrderResponse.getExecutedCommission())) {
                var commission = toBigDecimal(postOrderResponse.getExecutedCommission(), 8);
                log.info("Receive commission {} for order {} figi {} from postOrderResponse {}", commission, postOrderResponse.getOrderId(), postOrderResponse.getFigi(), postOrderResponse.getExecutedCommission());
                return commission;
            }
        }
        for (var i = 0; i < 10; i++) {
            try {
                log.info("Try number {} to request commission for order {} figi {}", i + 1, postOrderResponse.getOrderId(), postOrderResponse.getFigi());
                var orderState = getApi().getOrdersService().getOrderStateSync(getAccountId(), postOrderResponse.getOrderId());
                if (orderState.hasExecutedCommission()) {
                    if (!isZero(orderState.getExecutedCommission())) {
                        var commission = toBigDecimal(orderState.getExecutedCommission(), 8);
                        log.info("Receive commission {} for order {} figi {} from postOrderState {}", commission, postOrderResponse.getOrderId(), postOrderResponse.getFigi(), orderState.getExecutedCommission());
                        return commission;
                    }
                }
                TimeUnit.SECONDS.sleep(i + 1);
            } catch (Exception e) {
                log.info("Exception during request commission for order {} figi {}", postOrderResponse.getOrderId(), postOrderResponse.getFigi(), e);
                break;
            }
        }
        var commission = BigDecimal.ZERO;
        log.info("Failed to receive commission {} for order {} figi {}. Set zero commission {}", commission, postOrderResponse.getOrderId(), postOrderResponse.getFigi(), postOrderResponse.getInitialCommission());
        return commission;
    }

    public static BigDecimal toBigDecimal(MoneyValue value, Integer scale) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        var result = BigDecimal.valueOf(value.getUnits()).add(BigDecimal.valueOf(value.getNano(), 9));
        if (scale != null) {
            return result.setScale(scale, RoundingMode.HALF_EVEN);
        }
        return result;
    }

    public static BigDecimal toBigDecimal(Quotation value, Integer scale) {
        var result = BigDecimal.valueOf(value.getUnits()).add(BigDecimal.valueOf(value.getNano(), 9));
        if (scale != null) {
            return result.setScale(scale, RoundingMode.HALF_EVEN);
        }
        return result;
    }

    /**
     * @param value
     * @param scale
     * @param defaultIfZero - default value required because of executedPrice in sandbox = 0
     * @return
     */
    public static BigDecimal toBigDecimal(MoneyValue value, Integer scale, BigDecimal defaultIfZero) {
        var amount = toBigDecimal(value, scale);
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return defaultIfZero;
        }
        return amount;
    }
}
