package com.struchev.invest.webscraping;

import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.repository.ContractResultRepository;
import com.struchev.invest.repository.CurrencyRateRepository;
import com.struchev.invest.repository.InstrumentRepository;
import com.struchev.invest.repository.OrderRepository;
import com.struchev.invest.service.candle.CandleHistoryService;
import com.struchev.invest.service.dictionary.InstrumentService;
import com.struchev.invest.strategy.StrategySelector;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
public class MoexScrapingTests {

    @MockBean
    CurrencyRateRepository currencyRateRepository;
    @MockBean
    OrderRepository orderRepository;
    @MockBean
    InstrumentRepository instrumentRepository;
    @MockBean
    ContractResultRepository contractResultRepository;
    @Autowired
    InstrumentService instrumentService;
    @Autowired
    StrategySelector strategySelector;
    @MockBean
    CandleHistoryService candleHistoryService;

    MoexScrapingTask moexScrapingTask;
    @PostConstruct
    void setUp() {
        Mockito.when(orderRepository.findByPurchaseDateTimeGreaterThan(Mockito.any(OffsetDateTime.class))).thenAnswer(new Answer<List<OrderDomainEntity>>() {
            public List<OrderDomainEntity> answer(InvocationOnMock invocation) throws Throwable {
                List<OrderDomainEntity> list = new ArrayList<>();
                OrderDomainEntity orderNg = OrderDomainEntity.builder()
                        .figi("FUTNG0323000")
                        .build();
                OrderDomainEntity orderSi = OrderDomainEntity.builder()
                        .figi("FUTSI0623000")
                        .build();
                OrderDomainEntity orderSl = OrderDomainEntity.builder()
                        .figi("FUTSILV06230")
                        .build();
                OrderDomainEntity orderBr = OrderDomainEntity.builder()
                        .figi("FUTBR0423000")
                        .build();
                OrderDomainEntity orderGo = OrderDomainEntity.builder()
                        .figi("FUTGOLD06230")
                        .build();
                OrderDomainEntity orderNa = OrderDomainEntity.builder()
                        .figi("FUTNASD06230")
                        .build();
                list.add(orderNg);
                list.add(orderNg);
                list.add(orderSi);
                list.add(orderSl);
                list.add(orderBr);
                list.add(orderGo);
                list.add(orderNa);
                return list;
            }});
        moexScrapingTask = new MoexScrapingTask(
                currencyRateRepository,
                orderRepository,
                instrumentRepository,
                contractResultRepository,
                strategySelector,
                instrumentService
        );
    }
    @Test
    public void webScrapingDerivativeUsdRatesTest() {
        moexScrapingTask.getDerivativeUsdRates();
    }

    @Test
    public void webScrapingContractResultsTest() {
        moexScrapingTask.getContractResults();
    }

    @Test
    public void webScrapingDayContractResultsTest() {
        moexScrapingTask.getDayContractResults();
    }
}
