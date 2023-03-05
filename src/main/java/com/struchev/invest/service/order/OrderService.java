package com.struchev.invest.service.order;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.entity.OrderDetails;
import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.repository.OrderRepository;
import com.struchev.invest.service.dictionary.InstrumentService;
import com.struchev.invest.service.tinkoff.ITinkoffOrderAPI;
import com.struchev.invest.strategy.AStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final InstrumentService instrumentService;
    private final ITinkoffOrderAPI tinkoffOrderAPI;

    private volatile List<OrderDomainEntity> orders;

    public OrderDomainEntity findActiveByFigiAndStrategy(String figi, AStrategy strategy) {
        return orders.stream()
                .filter(o -> figi == null || o.getFigi().equals(figi))
                .filter(o -> o.getSellDateTime() == null)
                .filter(o -> o.getStrategy().equals(strategy.getName()))
                .findFirst().orElse(null);
    }

    public List<OrderDomainEntity> findClosedByFigiAndStrategy(String figi, AStrategy strategy) {
        return orders.stream()
                .filter(o -> o.getFigi().equals(figi))
                .filter(o -> o.getSellDateTime() != null)
                .filter(o -> o.getStrategy().equals(strategy.getName()))
                .collect(Collectors.toList());
    }

    public OrderDomainEntity findLastByFigiAndStrategy(String figi, AStrategy strategy) {
        return orders.stream()
                .filter(o -> figi == null || o.getFigi().equals(figi))
                .filter(o -> o.getSellDateTime() != null)
                .filter(o -> o.getStrategy().equals(strategy.getName()))
                .reduce((first, second) -> second).orElse(null);
    }

    @Transactional
    public synchronized OrderDomainEntity openOrder(CandleDomainEntity candle, AStrategy strategy, Map<String, BigDecimal> currentPrices) {
        if (currentPrices != null) {
            currentPrices = currentPrices.entrySet().stream()
                    .filter(e -> strategy.getFigies().containsKey(e.getKey()))
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        }
        var instrument = instrumentService.getInstrument(candle.getFigi());
        var order = OrderDomainEntity.builder()
                .currency(instrument.getCurrency())
                .figi(instrument.getFigi())
                .figiTitle(instrument.getName())
                .purchasePriceWanted(candle.getClosingPrice())
                .strategy(strategy.getName())
                .purchaseDateTime(candle.getDateTime())
                .lots(strategy.getCount(candle.getFigi()))
                .purchaseCommissionInitial(BigDecimal.ZERO)
                .details(OrderDetails.builder().currentPrices(currentPrices).build())
                .build();

        if (strategy.isCheckBook()
                && !tinkoffOrderAPI.checkGoodBuy(instrument, candle.getClosingPrice(), order.getLots(), strategy.getPriceError())) {
            throw new RuntimeException("checkGoodBuy return false for figi " + instrument.getFigi());
        }
        var result = tinkoffOrderAPI.buy(instrument, candle.getClosingPrice(), order.getLots());
        order.setPurchaseCommissionInitial(result.getCommissionInitial());
        order.setPurchaseCommission(result.getCommission());
        order.setPurchasePrice(result.getPrice());
        order.setPurchaseOrderId(result.getOrderId());
        order = orderRepository.save(order);
        orders.add(order);

        order = openLimitOrder(order, strategy, candle);

        return order;
    }

    @Transactional
    public synchronized OrderDomainEntity openLimitOrder(OrderDomainEntity order, AStrategy strategy, CandleDomainEntity candle) {
        var orderFresh = findActiveByFigiAndStrategy(order.getFigi(), strategy);
        if (orderFresh.getId() != order.getId()) {
            return order;
        }
        order = orderFresh;
        if (strategy.getSellLimitCriteria() == null) {
            return order;
        }
        if (strategy.getSellLimitCriteria().getExitProfitPercent() == null || strategy.getSellLimitCriteria().getExitProfitPercent() <= 0) {
            return order;
        }
        var instrument = instrumentService.getInstrument(order.getFigi());
        var limitPrice = order.getPurchasePrice().multiply(BigDecimal.valueOf((strategy.getSellLimitCriteria().getExitProfitPercent() + 100.)/100.));
        limitPrice = limitPrice.divide(instrument.getMinPriceIncrement(), 0, RoundingMode.HALF_UP).multiply(instrument.getMinPriceIncrement());
        var lots = order.getLots();
        if (order.getCellLots() != null) {
            lots -= order.getCellLots().intValue();
        }
        var result = tinkoffOrderAPI.sellLimit(instrument, limitPrice, lots, order.getSellLimitOrderUuid(), order.getSellLimitOrderId(), candle);
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
        if (null != result.getLots() && result.getLots() > 0) {
            order.setSellDateTime(candle.getDateTime());
            order = setOrderInfo(order, result);
        }
        return order;
    }

    @Transactional
    public synchronized OrderDomainEntity closeOrder(CandleDomainEntity candle, AStrategy strategy) {
        var instrument = instrumentService.getInstrument(candle.getFigi());
        var order = findActiveByFigiAndStrategy(candle.getFigi(), strategy);

        order.setSellPriceWanted(candle.getClosingPrice());
        order.setSellProfitWanted(order.getSellPriceWanted().subtract(order.getPurchasePrice()));

        if (strategy.isCheckBook() && order.getSellProfitWanted().compareTo(BigDecimal.ZERO) > 0
                && !tinkoffOrderAPI.checkGoodSell(instrument, candle.getClosingPrice(), order.getLots(), strategy.getPriceError())) {
            throw new RuntimeException("checkGoodSell return false for figi " + instrument.getFigi());
        }

        var lots = order.getLots();
        if (order.getCellLots() != null) {
            lots -= order.getCellLots().intValue();
        }
        if (order.getSellLimitOrderId() != null) {
            var closeResult = tinkoffOrderAPI.closeSellLimit(instrument, order.getSellLimitOrderId());
            if (null != closeResult.getLots() && closeResult.getLots() > 0) {
                order.setSellDateTime(OffsetDateTime.now());
                order = setOrderInfo(order, closeResult);
                lots -= closeResult.getLots().intValue();
            }
            if (closeResult.getOrderId() != null && closeResult.getOrderId().equals(order.getSellLimitOrderId())) {
                order.setSellLimitOrderId(closeResult.getOrderId());
            }
        }
        if (lots > 0) {
            var result = tinkoffOrderAPI.sell(instrument, candle.getClosingPrice(), lots);

            order.setSellDateTime(candle.getDateTime());
            order = setOrderInfo(order, result);
        }

        return order;
    }

    private OrderDomainEntity setOrderInfo(OrderDomainEntity order, ITinkoffOrderAPI.OrderResult result) {
        order.setSellOrderId(result.getOrderId());
        order.setSellCommissionInitial(result.getCommissionInitial());
        order.setSellCommission(result.getCommission());
        if (result.getOrderPrice() != null) {
            order.setSellPrice(result.getOrderPrice().divide(BigDecimal.valueOf(result.getLots()), 8, RoundingMode.HALF_DOWN));
        } else {
            order.setSellPrice(result.getPrice());
        }
        if (result.getLots() != null) {
            var lots = order.getCellLots() == null ? 0 : order.getCellLots();
            lots += result.getLots().intValue();
            order.setCellLots(lots);
        }
        order.setSellProfit(order.getSellPrice().subtract(order.getPurchasePrice()));
        return saveOrder(order);
    }

    private OrderDomainEntity saveOrder(OrderDomainEntity order) {
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
        orders.addAll(orderRepository.findAll(Sort.by("id")));
    }
}
