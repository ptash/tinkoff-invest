package com.struchev.invest.service.tinkoff;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.service.dictionary.InstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.*;
import ru.tinkoff.piapi.core.exception.ApiRuntimeException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
                instrument.getFigi(), quantity, quotation, OrderDirection.ORDER_DIRECTION_BUY, getAccountIdByFigi(instrument), OrderType.ORDER_TYPE_MARKET, uuid);

        if (getIsSandboxMode()) {
            var result = getApi().getSandboxService().postOrderSync(instrument.getFigi(), quantity, quotation,
                    OrderDirection.ORDER_DIRECTION_BUY, getAccountIdByFigi(instrument), OrderType.ORDER_TYPE_MARKET, uuid);
            return OrderResult.builder()
                    .orderId(result.getOrderId())
                    .commissionInitial(toBigDecimal(result.getInitialCommission(), 8))
                    .commission(toBigDecimal(result.getInitialCommission(), 8))
                    .price(toBigDecimal(result.getExecutedOrderPrice(), 8, price))
                    .pricePt(toBigDecimal(result.getInitialOrderPricePt(), 8))
                    .lots(result.getLotsRequested() * instrument.getLot())
                    .build();
        } else {
            checkInstrumentAvailableToBuy(instrument, price, count);
            var result = getApi().getOrdersService().postOrderSync(instrument.getFigi(), quantity, quotation,
                    OrderDirection.ORDER_DIRECTION_BUY, getAccountIdByFigi(instrument), OrderType.ORDER_TYPE_MARKET, uuid);
            return OrderResult.builder()
                    .orderId(result.getOrderId())
                    .commissionInitial(toBigDecimal(result.getInitialCommission(), 8))
                    .commission(getExecutedCommission(result, instrument))
                    .price(toBigDecimal(result.getExecutedOrderPrice(), 8, price))
                    .pricePt(toBigDecimal(result.getInitialOrderPricePt(), 8))
                    .lots(result.getLotsRequested() * instrument.getLot())
                    .build();
        }
    }

    public OrderResult closeSellLimit(InstrumentService.Instrument instrument, String orderId) {
        var orderResultBuilder = OrderResult.builder();
        if (null == orderId) {
            orderResultBuilder.build();
        }
        log.info("Close sell limit with: figi {}, acc {}, orderId {}",
                instrument.getFigi(), getAccountIdByFigi(instrument), orderId);
        try {
            if (getIsSandboxMode()) {
                getApi().getSandboxService().cancelOrderSync(getAccountIdByFigi(instrument), orderId);
            } else {
                getApi().getOrdersService().cancelOrderSync(getAccountIdByFigi(instrument), orderId);
            }
            return checkSellLimit(instrument, orderId);
        } catch (Exception e) {
            log.warn("Error in close sellLimit {}", instrument.getFigi(), e);
            List<OrderState> orders;
            if (getIsSandboxMode()) {
                orders = getApi().getSandboxService().getOrdersSync(getAccountIdByFigi(instrument));
            } else {
                orders = getApi().getOrdersService().getOrdersSync(getAccountIdByFigi(instrument));
            }
            var order = orders.stream().filter(o -> o.getFigi().equals(instrument.getFigi())).findFirst().orElse(null);
            if (order != null && !order.getOrderId().equals(orderId)) {
                return closeSellLimit(instrument, order.getOrderId());
            }
            orderResultBuilder.exception(e);
        }
        return OrderResult.builder().orderId(orderId).build();
    }

    private OrderResult checkSellLimit(InstrumentService.Instrument instrument, String orderId) {
        var orderResultBuilder = OrderResult.builder();
        if (null == orderId) {
            orderResultBuilder.build();
        }
        log.info("Check limit postOrderSync with: figi {}, acc {}, orderId {}",
                instrument.getFigi(), getAccountIdByFigi(instrument), orderId);

        try {
            orderResultBuilder.active(true);
            if (getIsSandboxMode()) {
                var result = getApi().getSandboxService().getOrderStateSync(getAccountIdByFigi(instrument), orderId);
                orderResultBuilder
                        .orderId(result.getOrderId());
                if (result.getExecutionReportStatus().getNumber() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_FILL_VALUE
                        || result.getExecutionReportStatus().getNumber() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL_VALUE
                ) {
                    if (result.hasExecutedOrderPrice() && !isZero(result.getExecutedOrderPrice())) {
                        orderResultBuilder.commissionInitial(toBigDecimal(result.getInitialCommission(), 8))
                                .commission(toBigDecimal(result.getInitialCommission(), 8))
                                .lots(result.getLotsExecuted() * instrument.getLot())
                                .orderPrice(toBigDecimal(result.getExecutedOrderPrice(), 8));
                    }
                } else if (result.getExecutionReportStatus().getNumber() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_REJECTED_VALUE
                        || result.getExecutionReportStatus().getNumber() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_CANCELLED_VALUE
                ) {
                    orderResultBuilder.active(false);
                }
            } else {
                var result = getApi().getOrdersService().getOrderStateSync(getAccountIdByFigi(instrument), orderId);
                orderResultBuilder
                        .orderId(result.getOrderId());
                if (result.getExecutionReportStatus().getNumber() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_FILL_VALUE
                        || result.getExecutionReportStatus().getNumber() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL_VALUE
                ) {
                    if (result.hasExecutedOrderPrice() && !isZero(result.getExecutedOrderPrice())) {
                        orderResultBuilder.commissionInitial(toBigDecimal(result.getInitialCommission(), 8))
                                .commission(getExecutedCommission(result, instrument))
                                .lots(result.getLotsExecuted() * instrument.getLot())
                                .orderPrice(toBigDecimal(result.getExecutedOrderPrice(), 8));
                    }
                } else if (result.getExecutionReportStatus().getNumber() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_REJECTED_VALUE
                        || result.getExecutionReportStatus().getNumber() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_CANCELLED_VALUE
                ) {
                    orderResultBuilder.active(false);
                }
            }
        } catch (Exception e) {
            log.warn("Error in check sellLimit {}", instrument.getFigi(), e);
            orderResultBuilder.exception(e);
        }
        return orderResultBuilder.build();
    }

    public OrderResult sellLimit(InstrumentService.Instrument instrument, BigDecimal price, Integer count, String uuid, String orderId, CandleDomainEntity candle) {
        var orderResultBuilder = OrderResult.builder();
        if (orderId != null) {
            var res = checkSellLimit(instrument, orderId);
            if (res.getActive()) {
                if ((instrument.getType() != InstrumentService.Type.future && res.getPrice().equals(price))
                        || (instrument.getType() == InstrumentService.Type.future && res.getPricePt().equals(price))
                ) {
                    return res;
                } else {
                    res = this.closeSellLimit(instrument, orderId);
                    if (res.getOrderId() != null) {
                        orderId = res.getOrderId();
                    }
                }
            }
            if (res.getLots() != null && res.getLots() > 0) {
                orderResultBuilder
                        .lots(res.lots)
                        .price(res.getPrice())
                        .pricePt(res.getPricePt())
                        .commission(res.getCommission());
            }
            // новую будем создавать
            uuid = null;
        }

        long quantity = count / instrument.getLot();
        var quotation = Quotation.newBuilder()
                .setUnits(price.longValue())
                .setNano(price.remainder(BigDecimal.ONE).movePointRight(9).intValue())
                .build();
        //if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            orderResultBuilder.orderUuid(uuid);
        //}
        log.info("Send limit postOrderSync with: figi {}, quantity {}, quotation {}, direction {}, acc {}, type {}, id {}",
                instrument.getFigi(), quantity, quotation, OrderDirection.ORDER_DIRECTION_SELL, getAccountIdByFigi(instrument), OrderType.ORDER_TYPE_LIMIT, uuid);

        try {
            if (getIsSandboxMode()) {
                var result = getApi().getSandboxService().postOrderSync(instrument.getFigi(), quantity, quotation,
                        OrderDirection.ORDER_DIRECTION_SELL, getAccountIdByFigi(instrument), OrderType.ORDER_TYPE_LIMIT, uuid);
                orderResultBuilder
                        .orderId(result.getOrderId());
                if (result.getExecutionReportStatus().getNumber() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_FILL_VALUE
                        || result.getExecutionReportStatus().getNumber() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL_VALUE
                ) {
                    if (result.hasExecutedOrderPrice() && !isZero(result.getExecutedOrderPrice())) {
                        orderResultBuilder.commissionInitial(toBigDecimal(result.getInitialCommission(), 8))
                                .commission(toBigDecimal(result.getInitialCommission(), 8))
                                .lots(result.getLotsExecuted() * instrument.getLot())
                                .orderPrice(toBigDecimal(result.getExecutedOrderPrice(), 8));
                    }
                }
            } else {
                checkInstrumentAvailableToSell(instrument, count);
                var result = getApi().getOrdersService().postOrderSync(instrument.getFigi(), quantity, quotation,
                        OrderDirection.ORDER_DIRECTION_SELL, getAccountIdByFigi(instrument), OrderType.ORDER_TYPE_LIMIT, uuid);
                orderResultBuilder
                        .orderId(result.getOrderId());
                if (result.getExecutionReportStatus().getNumber() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_FILL_VALUE
                        || result.getExecutionReportStatus().getNumber() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL_VALUE
                ) {
                    if (result.hasExecutedOrderPrice() && !isZero(result.getExecutedOrderPrice())) {
                        orderResultBuilder.commissionInitial(toBigDecimal(result.getInitialCommission(), 8))
                                .commission(getExecutedCommission(result, instrument))
                                .lots(result.getLotsExecuted() * instrument.getLot())
                                .orderPrice(toBigDecimal(result.getExecutedOrderPrice(), 8));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error in sellLimit {}", instrument.getFigi(), e);
            List<OrderState> orders;
            if (getIsSandboxMode()) {
                orders = getApi().getSandboxService().getOrdersSync(getAccountIdByFigi(instrument));
            } else {
                orders = getApi().getOrdersService().getOrdersSync(getAccountIdByFigi(instrument));
            }
            var order = orders.stream().filter(o -> o.getFigi().equals(instrument.getFigi())).findFirst().orElse(null);
            if (order != null && !order.getOrderId().equals(orderId)) {
                var res = closeSellLimit(instrument, order.getOrderId());
                orderResultBuilder.orderId(res.getOrderId());
            } else {
                orderResultBuilder.exception(e);
            }
        }
        return orderResultBuilder.build();
    }

    public OrderResult sell(InstrumentService.Instrument instrument, BigDecimal price, Integer count) {
        long quantity = count / instrument.getLot();
        var quotation = Quotation.newBuilder()
                .setUnits(price.longValue())
                .setNano(price.remainder(BigDecimal.ONE).movePointRight(9).intValue())
                .build();
        var uuid = UUID.randomUUID().toString();
        log.info("Send postOrderSync with: figi {}, quantity {}, quotation {}, direction {}, acc {}, type {}, id {}",
                instrument.getFigi(), quantity, quotation, OrderDirection.ORDER_DIRECTION_SELL, getAccountIdByFigi(instrument), OrderType.ORDER_TYPE_MARKET, uuid);

        if (getIsSandboxMode()) {
            var result = getApi().getSandboxService().postOrderSync(instrument.getFigi(), quantity, quotation,
                    OrderDirection.ORDER_DIRECTION_SELL, getAccountIdByFigi(instrument), OrderType.ORDER_TYPE_MARKET, uuid);
            result.getOrderId();
            return OrderResult.builder()
                    .orderId(result.getOrderId())
                    .commissionInitial(toBigDecimal(result.getInitialCommission(), 8))
                    .commission(toBigDecimal(result.getInitialCommission(), 8))
                    .price(toBigDecimal(result.getExecutedOrderPrice(), 8, price))
                    .pricePt(toBigDecimal(result.getInitialOrderPricePt(), 8))
                    .lots(result.getLotsRequested() * instrument.getLot())
                    .build();
        } else {
            checkInstrumentAvailableToSell(instrument, count);
            var result = getApi().getOrdersService().postOrderSync(instrument.getFigi(), quantity, quotation,
                    OrderDirection.ORDER_DIRECTION_SELL, getAccountIdByFigi(instrument), OrderType.ORDER_TYPE_MARKET, uuid);
            return OrderResult.builder()
                    .orderId(result.getOrderId())
                    .commissionInitial(toBigDecimal(result.getInitialCommission(), 8))
                    .commission(getExecutedCommission(result, instrument))
                    .price(toBigDecimal(result.getExecutedOrderPrice(), 8, price))
                    .pricePt(toBigDecimal(result.getInitialOrderPricePt(), 8))
                    .lots(result.getLotsRequested() * instrument.getLot())
                    .build();
        }
    }

    private void checkInstrumentAvailableToSell(InstrumentService.Instrument instrument, Integer count) {
        var positionsResult = getApi().getOperationsService().getPositionsSync(getAccountIdByFigi(instrument));
        long balanceCount = 0;
        var annotate = "";
        if (instrument.getType() == InstrumentService.Type.share) {
            var ourSecurities = positionsResult.getSecurities().stream().filter(p -> p.getFigi().equals(instrument.getFigi())).collect(Collectors.toList());
            if (ourSecurities.size() > 0) {
                balanceCount = ourSecurities.get(0).getBalance();
                annotate += " securities: " + balanceCount;
            }
        }
        if (instrument.getType() == InstrumentService.Type.future) {
            var ourSecurities = positionsResult.getFutures().stream().filter(p -> p.getFigi().equals(instrument.getFigi())).collect(Collectors.toList());
            if (ourSecurities.size() > 0) {
                balanceCount = ourSecurities.get(0).getBalance();
                annotate += " futures: " + balanceCount;
            }
        }
        if (balanceCount >= count) {
            return;
        }
        log.warn("Sell is not available for {} in count of {}. ", instrument.getFigi(), count, annotate);
        throw new RuntimeException("Sell is not available for " + instrument.getFigi() + " in count of " + count + ". "
                + annotate);
    }

    private void checkInstrumentAvailableToBuy(InstrumentService.Instrument instrument, BigDecimal price, Integer count) {
        var positionsResult = getApi().getOperationsService().getPositionsSync(getAccountIdByFigi(instrument));
        var money = positionsResult.getMoney().stream().filter(m -> m.getCurrency().equals(instrument.getCurrency())).findFirst().get();
        BigDecimal balance = BigDecimal.ZERO;
        var annotate = "";
        if (null != money) {
            balance = money.getValue();
            annotate += " balance: " + balance;
        }
        if (instrument.getType() == InstrumentService.Type.future) {
            var featureMargin = getApi().getInstrumentsService().getFuturesMarginSync(instrument.getFigi());
            var initialMargin = toBigDecimal(featureMargin.getInitialMarginOnSell(), 8);
            annotate += " initialMargin: " + initialMargin;
            price = initialMargin.multiply(BigDecimal.valueOf(2));
        }
        var total = price.multiply(BigDecimal.valueOf(count));
        if (money.getValue().compareTo(total) > 0) {
            return;
        }
        throw new RuntimeException("Buy is not available for " + instrument.getFigi() + " in count of " + count
                + " price " + price + " = " + total + ". "
                + annotate);
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
        //if (currentDelta.compareTo(priceError) > 0) {
        //    log.info("Sell " + instrument.getFigi() + " error: the ask price " + askPrice + " differs from the bid " + bidPrice + " price by more than " + priceError + " < " + currentDelta);
        //    return false;
        //}
        var delta = price.multiply(priceError);
        delta = moneyRound(instrument, delta);
        if (price.compareTo(bidPrice.add(delta)) > 0) {
            throw new RuntimeException("Sell " + instrument.getFigi() + " error: the bid price " + bidPrice + " + " + delta + " is less than wanted price " + price);
            //return false;
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
        var delta = price.multiply(priceError);
        delta = moneyRound(instrument, delta);
        if (price.compareTo(askPrice.subtract(delta)) < 0) {
            throw new RuntimeException("Buy " + instrument.getFigi() + " error: the ask price " + askPrice + " - " + delta + " is more than wanted price " + price);
        }
        //if (currentDelta.compareTo(priceError) > 0) {
        //    throw new RuntimeException("Buy " + instrument.getFigi() + " error: the ask price " + askPrice + " differs from the bid " + bidPrice + " price by more than " + priceError + " < " + currentDelta);
        //}
        return true;
    }

    private BigDecimal moneyRound(InstrumentService.Instrument instrument, BigDecimal price) {
        return price.divide(instrument.getMinPriceIncrement(), 0, RoundingMode.HALF_UP).multiply(instrument.getMinPriceIncrement());
    }

    private Boolean isZero(MoneyValue money) {
        return money.getNano() == 0 && money.getUnits() == 0;
    }

    private BigDecimal getExecutedCommission(OrderState orderState, InstrumentService.Instrument instrument) {
        return getExecutedCommission(instrument, orderState.getOrderId(), orderState.getInitialCommission(), orderState.getExecutedCommission());
    }
    private BigDecimal getExecutedCommission(PostOrderResponse postOrderResponse, InstrumentService.Instrument instrument) {
        return getExecutedCommission(instrument, postOrderResponse.getOrderId(), postOrderResponse.getInitialCommission(), postOrderResponse.getExecutedCommission());
    }

    private BigDecimal getExecutedCommission(InstrumentService.Instrument instrument, String orderId, MoneyValue initialCommission, MoneyValue executedCommission) {
        var figi = instrument.getFigi();
        if (null != executedCommission) {
            if (!isZero(executedCommission) || isZero(initialCommission)) {
                var commission = toBigDecimal(executedCommission, 8);
                log.info("Receive commission {} for order {} figi {} from postOrderResponse {}", commission, orderId, figi, executedCommission);
                return commission;
            }
        }
        for (var i = 0; i < 10; i++) {
            try {
                log.info("Try number {} to request commission for order {} figi {}", i + 1, orderId, figi);
                var orderState = getApi().getOrdersService().getOrderStateSync(getAccountIdByFigi(instrument), orderId);
                if (orderState.hasExecutedCommission()) {
                    if (!isZero(orderState.getExecutedCommission()) || isZero(orderState.getInitialCommission())) {
                        var commission = toBigDecimal(orderState.getExecutedCommission(), 8);
                        log.info("Receive commission {} for order {} figi {} from postOrderState {}", commission, orderId, figi, orderState.getExecutedCommission());
                        return commission;
                    }
                }
                TimeUnit.SECONDS.sleep(i + 1);
            } catch (Exception e) {
                log.info("Exception during request commission for order {} figi {}", orderId, figi, e);
                break;
            }
        }
        var commission = BigDecimal.ZERO;
        if (null != initialCommission) {
            commission = toBigDecimal(initialCommission, 8);
        }
        log.info("Failed to receive commission {} for order {} figi {}. Set zero commission {}", commission, orderId, figi, initialCommission);
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
