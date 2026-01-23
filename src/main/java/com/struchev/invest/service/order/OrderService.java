package com.struchev.invest.service.order;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.entity.OrderDetails;
import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.repository.OrderRepository;
import com.struchev.invest.service.candle.CandleHistoryReverseForShortService;
import com.struchev.invest.service.dictionary.InstrumentService;
import com.struchev.invest.service.tinkoff.ITinkoffOrderAPI;
import com.struchev.invest.strategy.AStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService implements IOrderService {
    private final OrderRepository orderRepository;
    private final InstrumentService instrumentService;
    private final ITinkoffOrderAPI tinkoffOrderAPI;
    private final CandleHistoryReverseForShortService candleHistoryReverseForShortService;

    private volatile List<OrderDomainEntity> orders;

    public Order findActiveByFigiAndStrategy(String figi, AStrategy strategy) {
        var order = findActiveOrderDomainByFigiAndStrategy(figi, strategy);
        if (order == null) {
            return null;
        }
        return Order.builder()
                .orderDomainEntity(order)
                .purchasePrice(order.getPurchasePrice())
                .purchaseDateTime(order.getPurchaseDateTime())
                .details(order.getDetails())
                .build();
    }

    public OrderDomainEntity findActiveOrderDomainByFigiAndStrategy(String figi, AStrategy strategy) {
        return orders.stream()
                .filter(o -> figi == null || o.getFigi().equals(figi))
                .filter(o -> o.getSellDateTime() == null)
                .filter(o -> o.getStrategy().equals(strategy.getName()))
                .findFirst().orElse(null);
    }

    public OrderDomainEntity findAnyActiveOrderDomainByFigiAndStrategy(String figi, AStrategy strategy) {
        return orders.stream()
                .filter(o -> figi == null || o.getFigi().equals(figi))
                .filter(o -> o.getSellDateTime() == null || o.getPurchaseDateTime() == null)
                .filter(o -> o.getStrategy().equals(strategy.getName()))
                .findFirst().orElse(null);
    }

    public OrderDomainEntity findActiveOrderDomainShortByFigiAndStrategy(String figi, AStrategy strategy) {
        return orders.stream()
                .filter(o -> figi == null || o.getFigi().equals(figi))
                .filter(o -> o.getPurchaseDateTime() == null)
                .filter(o -> o.getStrategy().equals(strategy.getName()))
                .findFirst().orElse(null);
    }

    public OrderDomainEntity findById(Long id) {
        return orders.stream()
                .filter(o -> o.getId() != null && o.getId().equals(id))
                .findFirst().orElse(null);
    }

    public List<OrderDomainEntity> findClosedByFigiAndStrategy(String figi, AStrategy strategy) {
        return orders.stream()
                .filter(o -> o.getFigi().equals(figi))
                .filter(o -> o.getSellDateTime() != null && o.getPurchaseDateTime() != null)
                .filter(o -> o.getStrategy().equals(strategy.getName()))
                .collect(Collectors.toList());
    }

    public Order findLastByFigiAndStrategy(String figi, AStrategy strategy) {
        var order = findLastOrderDomainByFigiAndStrategy(figi, strategy);
        if (order == null) {
            return null;
        }
        return Order.builder()
                .orderDomainEntity(order)
                .purchasePrice(order.getPurchasePrice())
                .purchaseDateTime(order.getPurchaseDateTime())
                .sellPrice(order.getSellPrice())
                .sellDateTime(order.getSellDateTime())
                .details(order.getDetails())
                .sellProfit(order.getSellProfit())
                .build();
    }

    public OrderDomainEntity findLastOrderDomainByFigiAndStrategy(String figi, AStrategy strategy) {
        return orders.stream()
                .filter(o -> figi == null || o.getFigi().equals(figi))
                .filter(o -> o.getSellDateTime() != null)
                .filter(o -> o.getPurchaseDateTime() != null)
                .filter(o -> !o.getPurchaseDateTime().isAfter(o.getSellDateTime()))
                .filter(o -> o.getStrategy().equals(strategy.getName()))
                .reduce((first, second) -> second).orElse(null);
    }

    public OrderDomainEntity findLastShortOrderDomainByFigiAndStrategy(String figi, AStrategy strategy) {
        return orders.stream()
                .filter(o -> figi == null || o.getFigi().equals(figi))
                .filter(o -> o.getSellDateTime() != null)
                .filter(o -> o.getPurchaseDateTime() != null)
                .filter(o -> o.getPurchaseDateTime().isAfter(o.getSellDateTime()))
                .filter(o -> o.getStrategy().equals(strategy.getName()))
                .reduce((first, second) -> second).orElse(null);
    }

    @Transactional
    public synchronized OrderDomainEntity openOrderShort(CandleDomainEntity candle, AStrategy strategy, OrderDetails orderDetails) {
        var instrument = instrumentService.getInstrument(candle.getFigi());
        var priceWanted = candle.getClosingPrice();
        if (orderDetails.getPriceWanted() != null) {
            //priceWanted = candleHistoryReverseForShortService.preparePrice(orderDetails.getPriceWanted()).min(candle.getLowestPrice());
            priceWanted = candleHistoryReverseForShortService.preparePrice(orderDetails.getPriceWanted());
        }
        var order = OrderDomainEntity.builder()
                .currency(instrument.getCurrency())
                .figi(instrument.getFigi())
                .figiTitle(instrument.getName())
                .sellPriceWanted(priceWanted)
                .strategy(strategy.getName())
                .sellDateTime(candle.getDateTime())
                .isShort(true)
                .lots(strategy.getCount(candle.getFigi()))
                .sellCommissionInitial(BigDecimal.ZERO)
                .purchaseCommission(BigDecimal.ZERO)
                .details(orderDetails)
                .build();

        if (strategy.isCheckBook()
                && !tinkoffOrderAPI.checkGoodSell(instrument, priceWanted, order.getLots(), strategy.getPriceError(), candle)) {
            throw new RuntimeException("checkGoodSell return false for figi " + instrument.getFigi() + " and strategy " + strategy.getExtName());
        }
        var result = tinkoffOrderAPI.buyShort(instrument, priceWanted, order.getLots(), candle);
        order.setSellCommissionInitial(result.getCommissionInitial());
        order.setSellCommission(result.getCommission());
        order.setSellPriceMoney(result.getPrice());
        if (instrument.getType() == InstrumentService.Type.future) {
            order.setSellPrice(result.getPricePt());
        } else {
            order.setSellPrice(result.getPrice());
        }
        order.setSellOrderId(result.getOrderId());
        order = orderRepository.save(order);
        orders.add(order);

        order = openLimitOrder(order, strategy, candle);
        return order;
    }

    @Transactional
    public synchronized OrderDomainEntity openOrder(CandleDomainEntity candle, AStrategy strategy, OrderDetails orderDetails) {
        var instrument = instrumentService.getInstrument(candle.getFigi());
        var priceWanted = candle.getClosingPrice();
        if (orderDetails.getPriceWanted() != null) {
            //priceWanted = orderDetails.getPriceWanted().max(candle.getLowestPrice());
            priceWanted = orderDetails.getPriceWanted();
        }
        var order = OrderDomainEntity.builder()
                .currency(instrument.getCurrency())
                .figi(instrument.getFigi())
                .figiTitle(instrument.getName())
                .purchasePriceWanted(priceWanted)
                .strategy(strategy.getName())
                .purchaseDateTime(candle.getDateTime())
                .isShort(false)
                .lots(strategy.getCount(candle.getFigi()))
                .purchaseCommissionInitial(BigDecimal.ZERO)
                .details(orderDetails)
                .build();

        if (strategy.isCheckBook()
                && !tinkoffOrderAPI.checkGoodBuy(instrument, priceWanted, order.getLots(), strategy.getPriceError(), candle)) {
            throw new RuntimeException("checkGoodBuy return false for figi " + instrument.getFigi() + " priceWanted " + priceWanted + " candle" + candle);
        }
        var result = tinkoffOrderAPI.buy(instrument, priceWanted, order.getLots(), candle);
        order.setPurchaseCommissionInitial(result.getCommissionInitial());
        order.setPurchaseCommission(result.getCommission());
        order.setPurchasePriceMoney(result.getPrice());
        if (instrument.getType() == InstrumentService.Type.future) {
            order.setPurchasePrice(result.getPricePt());
        } else {
            order.setPurchasePrice(result.getPrice());
        }
        order.setPurchaseOrderId(result.getOrderId());
        order = orderRepository.save(order);
        orders.add(order);

        order = openLimitOrder(order, strategy, candle);

        return order;
    }

    //@Transactional
    public synchronized OrderDomainEntity openLimitOrder(OrderDomainEntity order, AStrategy strategy, CandleDomainEntity candle) {
        OrderDomainEntity orderFresh;
        if (order.isShort()) {
            orderFresh = findActiveOrderDomainShortByFigiAndStrategy(order.getFigi(), strategy);
        } else {
            orderFresh = findActiveOrderDomainByFigiAndStrategy(order.getFigi(), strategy);
        }
        if (null == orderFresh || orderFresh.getId() != order.getId()) {
            log.info("Skip limit figi {}: {} != {}", candle.getFigi(), (null == orderFresh ? null : orderFresh.getId()), order.getId());
            return order;
        }
        order = orderFresh;
        if (strategy.getSellLimitCriteria(candle.getFigi()) == null) {
            log.info("Skip limit figi {}: getSellLimitCriteria = null", candle.getFigi());
            return order;
        }
        BigDecimal limitPercent = order.getDetails().getLimitPercent();
        if (limitPercent == null && null != strategy.getSellLimitCriteria(candle.getFigi()).getExitProfitPercent()) {
            limitPercent = BigDecimal.valueOf(strategy.getSellLimitCriteria(candle.getFigi()).getExitProfitPercent());
        }
        if (
                limitPercent == null
                //|| strategy.getSellLimitCriteria(candle.getFigi()).getExitProfitPercent() <= 0
        ) {
            //log.info("Skip limit figi {}: getExitProfitPercent = {}", candle.getFigi(), limitPercent);
            return order; //?
        }
        var instrument = instrumentService.getInstrument(order.getFigi());
        BigDecimal limitPrice;
        if (order.isShort()) {
            limitPrice = order.getSellPrice().multiply(BigDecimal.valueOf((100. - limitPercent.doubleValue())/100.));
            //log.info("limitPrice {} = {} * (100 - {})/100", limitPrice, order.getSellPrice(), limitPercent);
        } else {
            limitPrice = order.getPurchasePrice().multiply(BigDecimal.valueOf((limitPercent.doubleValue() + 100.)/100.));
        }
        //log.info("Increment {}", instrument.getMinPriceIncrement());
        if (!instrument.getMinPriceIncrement().equals(BigDecimal.ZERO) && instrument.getMinPriceIncrement().compareTo(BigDecimal.valueOf(0.00000001f)) > 0) {
            try {
                //log.info("Increment {} before {}", instrument.getMinPriceIncrement(), limitPrice);
                limitPrice = limitPrice.divide(instrument.getMinPriceIncrement(), 0, order.isShort() ? RoundingMode.HALF_DOWN : RoundingMode.HALF_UP).multiply(instrument.getMinPriceIncrement());
                //log.info("Increment {} after {}", instrument.getMinPriceIncrement(), limitPrice);
            } catch (ArithmeticException $e) {
                log.error("An error in limitPrice " + limitPrice + " to MinPriceIncrement " + instrument.getMinPriceIncrement(), $e);
            }
        }
        BigDecimal limitProfitPercent;
        if (order.isShort()) {
            limitProfitPercent = candle.getClosingPrice().divide(limitPrice, 8, RoundingMode.HALF_DOWN).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100));
        } else {
            limitProfitPercent = limitPrice.divide(candle.getClosingPrice(), 8, RoundingMode.HALF_DOWN).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100));
        }
        if (limitProfitPercent.compareTo(BigDecimal.valueOf(10)) > 0) {
            log.info("Skip limit figi {} price {}: {}% > 10%", candle.getFigi(), limitPrice, limitProfitPercent);
            return order;
        }
        var lots = order.getLots();
        if (order.getCellLots() != null) {
            lots -= order.getCellLots().intValue();
        }
        ITinkoffOrderAPI.OrderResult result;
        if (order.isShort()) {
            result = tinkoffOrderAPI.sellLimitShort(instrument, limitPrice, lots, order.getSellLimitOrderUuid(), order.getSellLimitOrderId(), candle, order);
        } else {
            result = tinkoffOrderAPI.sellLimit(instrument, limitPrice, lots, order.getSellLimitOrderUuid(), order.getSellLimitOrderId(), candle, order);
        }
        var needSave = false;
        if (null != result.getOrderUuid() && (null == order.getSellLimitOrderUuid() || !result.getOrderUuid().equals(order.getSellLimitOrderUuid()))) {
            order.setSellLimitOrderUuid(result.getOrderUuid());
            needSave = true;
        }
        if (null != result.getOrderId() && (null == order.getSellLimitOrderId() || !result.getOrderId().equals(order.getSellLimitOrderId()))) {
            order.setSellLimitOrderId(result.getOrderId());
            needSave = true;
        }
        if (order.getSellPriceLimitWanted() == null || !order.getSellPriceLimitWanted().equals(limitPrice)) {
            order.setSellPriceLimitWanted(limitPrice);
            needSave = true;
        }
        if (needSave) {
            order = saveOrder(order);
        }
        if (null != result.getLots() && result.getLots() > 0 && result.getIsExecuted()) {
            if (order.isShort()) {
                order = setOrderInfoBuy(order, result, candle);
            } else {
                order = setOrderInfoSell(order, result, candle);
            }
        }
        return order;
    }

    //@Transactional
    public synchronized OrderDomainEntity closeOrderShort(CandleDomainEntity candle, AStrategy strategy) throws Exception {
        var instrument = instrumentService.getInstrument(candle.getFigi());
        var order = findActiveOrderDomainShortByFigiAndStrategy(candle.getFigi(), strategy);

        order.setPurchasePriceWanted(candle.getClosingPrice());
        order.setSellProfitWanted(order.getSellPrice().subtract(order.getPurchasePriceWanted()));

        if (strategy.isCheckBook() && order.getSellProfitWanted().compareTo(BigDecimal.ZERO) > 0
                && !tinkoffOrderAPI.checkGoodBuy(instrument, candle.getClosingPrice(), order.getLots(), strategy.getPriceError(), candle)) {
            throw new RuntimeException("checkGoodBuy return false for figi " + instrument.getFigi());
        }

        order = closeSellLimit(order, instrument, candle);
        var lots = order.getLots();
        if (order.getCellLots() != null) {
            lots -= order.getCellLots().intValue();
        }

        if (lots > 0) {
            var result = tinkoffOrderAPI.sellShort(instrument, candle.getClosingPrice(), lots, candle);
            order = setOrderInfoBuy(order, result, candle);
        }

        return order;
    }

    //@Transactional
    public synchronized OrderDomainEntity closeOrder(CandleDomainEntity candle, AStrategy strategy) throws Exception {
        var instrument = instrumentService.getInstrument(candle.getFigi());
        var order = findActiveOrderDomainByFigiAndStrategy(candle.getFigi(), strategy);

        order.setSellPriceWanted(candle.getClosingPrice());
        order.setSellProfitWanted(order.getSellPriceWanted().subtract(order.getPurchasePrice()));

        if (strategy.isCheckBook() && order.getSellProfitWanted().compareTo(BigDecimal.ZERO) > 0
                && !tinkoffOrderAPI.checkGoodSell(instrument, candle.getClosingPrice(), order.getLots(), strategy.getPriceError(), candle)) {
            throw new RuntimeException("checkGoodSell return false for figi " + instrument.getFigi());
        }

        order = closeSellLimit(order, instrument, candle);
        var lots = order.getLots();
        if (order.getCellLots() != null) {
            lots -= order.getCellLots().intValue();
        }
        if (lots > 0) {
            var result = tinkoffOrderAPI.sell(instrument, candle.getClosingPrice(), lots, candle);
            order = setOrderInfoSell(order, result, candle);
        }

        return order;
    }

    private OrderDomainEntity closeSellLimit(OrderDomainEntity order, InstrumentService.Instrument instrument, CandleDomainEntity candle) throws Exception {
        var closeResult = tinkoffOrderAPI.closeSellLimit(instrument, order.getSellLimitOrderId(), candle);
        if (null != closeResult.getLots() && closeResult.getLots() > 0 && closeResult.getIsExecuted()) {
            if (order.isShort()) {
                order = setOrderInfoBuy(order, closeResult, candle);
            } else {
                order = setOrderInfoSell(order, closeResult, candle);
            }
        } else {
            closeResult = tinkoffOrderAPI.closeAllSellLimit(instrument, candle);
            if (null != closeResult.getLots() && closeResult.getLots() > 0 && closeResult.getIsExecuted()) {
                if (order.isShort()) {
                    order = setOrderInfoBuy(order, closeResult, candle);
                } else {
                    order = setOrderInfoSell(order, closeResult, candle);
                }
            }
        }
        if (
                closeResult.getOrderId() != null
                && closeResult.getOrderId().equals(order.getSellLimitOrderId())
        ) {
            order.setSellLimitOrderId(closeResult.getOrderId());
            order = saveOrder(order);
        }
        log.info("closeResult for {} {}", instrument.getFigi(), closeResult);
        if (
                closeResult.getException() != null
                && closeResult.getActive()
                && !closeResult.getIsExecuted()
        ) {
            throw closeResult.getException();
        }
        return order;
    }

    //@Transactional
    public synchronized void updateDetailsCurrentPrice(Order order, String key, BigDecimal price) {
        OrderDomainEntity orderFresh = findById(order.getOrderDomainEntity().getId());
        orderFresh.setDetails(order.getDetails());
        orderFresh.getDetails().getCurrentPrices().put(key, price);
        var newOrderDomainEntity = saveOrder(orderFresh);
        order.setOrderDomainEntity(newOrderDomainEntity);
    }

    private OrderDomainEntity setOrderInfoBuy(OrderDomainEntity order, ITinkoffOrderAPI.OrderResult result, CandleDomainEntity candle) {
        order.setPurchaseOrderId(result.getOrderId());
        order.setPurchaseCommissionInitial(result.getCommissionInitial());
        order.setPurchaseCommission(result.getCommission());
        var instrument = instrumentService.getInstrument(order.getFigi());

        order.setPurchasePriceMoney(result.getPrice());
        if (instrument.getType() == InstrumentService.Type.future) {
            order.setPurchasePrice(result.getPricePt());
        } else {
            order.setPurchasePrice(result.getPrice());
        }

        var isPurchaseAllDone = true;
        if (result.getLots() != null) {
            var lots = order.getCellLots() == null ? 0 : order.getCellLots();
            if (
                    result.getOrderId() != null
                    && result.getOrderId().equals(order.getSellLimitOrderId())
            ) {
                var key = "sl" + result.getOrderId();
                var prevLotsValue = order.getDetails().getCurrentInts().getOrDefault(key, 0);
                var newLots = result.getLots().intValue() - prevLotsValue;
                if (newLots > 0) {
                    log.info("setOrderInfoBuy {} add lots {} to by sellLimitId {}", candle.getFigi(), newLots, prevLotsValue, result.getOrderId());
                    lots += newLots;
                    order.getDetails().getCurrentInts().put(key, result.getLots().intValue());
                }
                isPurchaseAllDone = order.getLots() <= lots;
            } else {
                lots += result.getLots().intValue();
            }
            order.setCellLots(lots);
        }
        order.setSellProfit(order.getSellPrice().subtract(order.getPurchasePrice()));
        if (isPurchaseAllDone) {
            order.setPurchaseDateTime(candle.getDateTime());
        }
        return saveOrder(order);
    }

    private OrderDomainEntity setOrderInfoSell(OrderDomainEntity order, ITinkoffOrderAPI.OrderResult result, CandleDomainEntity candle) {
        order.setSellOrderId(result.getOrderId());
        order.setSellCommissionInitial(result.getCommissionInitial());
        order.setSellCommission(result.getCommission());
        var instrument = instrumentService.getInstrument(order.getFigi());

        order.setSellPriceMoney(result.getPrice());
        if (instrument.getType() == InstrumentService.Type.future) {
            order.setSellPrice(result.getPricePt());
        } else {
            order.setSellPrice(result.getPrice());
        }

        var isSellAllDone = true;
        if (result.getLots() != null) {
            var lots = order.getCellLots() == null ? 0 : order.getCellLots();
            if (
                    result.getOrderId() != null
                    && result.getOrderId().equals(order.getSellLimitOrderId())
            ) {
                var key = "sl" + result.getOrderId();
                var prevLotsValue = order.getDetails().getCurrentInts().getOrDefault(key, 0);
                var newLots = result.getLots().intValue() - prevLotsValue;
                if (newLots > 0) {
                    log.info("setOrderInfoSell {} add lots {} to by sellLimitId {}", candle.getFigi(), newLots, prevLotsValue, result.getOrderId());
                    lots += newLots;
                    order.getDetails().getCurrentInts().put(key, result.getLots().intValue());
                }
                isSellAllDone = order.getLots() <= lots;
            } else {
                lots += result.getLots().intValue();
            }
            order.setCellLots(lots);
        }
        order.setSellProfit(order.getSellPrice().subtract(order.getPurchasePrice()));
        if (isSellAllDone) {
            order.setSellDateTime(candle.getDateTime());
        }
        return saveOrder(order);
    }

    private synchronized OrderDomainEntity saveOrder(OrderDomainEntity order) {
        log.info("Save order {}", order);
        order = orderRepository.save(order);

        var orderId = order.getId();
        var orderInList = orders.stream().filter(o -> o.getId().equals(orderId)).findFirst().orElseThrow();
        orders.remove(orderInList);
        orders.add(order);
        return order;
    }

    @PostConstruct
    public void loadOrdersFromDB() {
        orders = new CopyOnWriteArrayList();
        var loaded = orderRepository.findAll(Sort.by(Sort.Direction.DESC,"id"))
                .stream().limit(200).collect(Collectors.toList());
        log.info("Load orders from DB count {}", loaded.size());
        orders.addAll(loaded);
    }
}
