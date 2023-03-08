package com.struchev.invest.service.tinkoff;

import com.struchev.invest.entity.CandleDomainEntity;
import com.struchev.invest.service.dictionary.InstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tinkoff.emulator", havingValue = "true", matchIfMissing = true)
public class TinkoffMockAPI extends ATinkoffAPI {

    private final BigDecimal PERCENT = new BigDecimal("0.00025");

    @Override
    public OrderResult buy(InstrumentService.Instrument instrument, BigDecimal price, Integer count) {
        return OrderResult.builder()
                .commissionInitial(calculateCommission(price, count))
                .commission(calculateCommission(price, count))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
    }

    @Override
    public OrderResult sell(InstrumentService.Instrument instrument, BigDecimal price, Integer count) {
        return OrderResult.builder()
                .commissionInitial(calculateCommission(price, count))
                .commission(calculateCommission(price, count))
                .price(price)
                .pricePt(price)
                .isExecuted(true)
                .build();
    }

    public OrderResult sellLimit(InstrumentService.Instrument instrument, BigDecimal price, Integer count, String uuid, String orderId, CandleDomainEntity candle) {
        if (candle.getClosingPrice().compareTo(price) >= 0) {
            return OrderResult.builder()
                    .orderUuid(UUID.randomUUID().toString())
                    .orderId(UUID.randomUUID().toString())
                    .commission(calculateCommission(price, count))
                    .lots(count.longValue())
                    .orderPrice(price.multiply(BigDecimal.valueOf(count)))
                    .price(price)
                    .pricePt(price)
                    .isExecuted(true)
                    .build();
        }
        return OrderResult.builder().build();
    }

    public OrderResult closeSellLimit(InstrumentService.Instrument instrument, String orderId) {
        return OrderResult.builder()
                .isExecuted(false)
                .build();
    }

    @Override
    public Boolean checkGoodSell(InstrumentService.Instrument instrument, BigDecimal price, Integer count, BigDecimal priceError) {
        return true;
    }

    @Override
    public Boolean checkGoodBuy(InstrumentService.Instrument instrument, BigDecimal price, Integer count, BigDecimal priceError) {
        return true;
    }

    private BigDecimal calculateCommission(BigDecimal price, Integer count) {
        var commission = price.multiply(PERCENT).multiply(BigDecimal.valueOf(count));
        //var commission = price.multiply(PERCENT).multiply(BigDecimal.valueOf(count)).setScale(2, RoundingMode.UP);
        return commission;
    }

}
