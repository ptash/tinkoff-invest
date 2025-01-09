package com.struchev.invest.webscraping;

import com.struchev.invest.entity.OrderDomainEntity;
import com.struchev.invest.repository.ContractResultRepository;
import com.struchev.invest.repository.CurrencyRateRepository;
import com.struchev.invest.repository.InstrumentRepository;
import com.struchev.invest.repository.OrderRepository;
import com.struchev.invest.service.candle.CandleHistoryService;
import com.struchev.invest.service.dictionary.InstrumentService;
import com.struchev.invest.strategy.StrategySelector;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;
import static org.junit.Assert.*;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

//@SpringBootTest
@Slf4j
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
        Mockito.when(contractResultRepository.save(Mockito.any())).thenAnswer(i -> {
            return i.getArguments()[0];
        });
        Mockito.when(orderRepository.findByPurchaseDateTimeGreaterThan(Mockito.any(OffsetDateTime.class))).thenAnswer(new Answer<List<OrderDomainEntity>>() {
            public List<OrderDomainEntity> answer(InvocationOnMock invocation) throws Throwable {
                List<OrderDomainEntity> list = new ArrayList<>();
                OrderDomainEntity orderNg = OrderDomainEntity.builder().figi("FUTNG0324000").build();
                OrderDomainEntity orderNg2 = OrderDomainEntity.builder().figi("FUTNG0424000").build();
                OrderDomainEntity orderSi = OrderDomainEntity.builder().figi("FUTSI0624000").build();
                OrderDomainEntity orderSl = OrderDomainEntity.builder().figi("FUTSILV06240").build();
                OrderDomainEntity orderBr = OrderDomainEntity.builder().figi("FUTBR0424000").build();
                OrderDomainEntity orderGo = OrderDomainEntity.builder().figi("FUTGOLD06240").build();
                OrderDomainEntity orderGoG = OrderDomainEntity.builder().figi("FUTGLDRUBF00").build();
                OrderDomainEntity orderNa = OrderDomainEntity.builder().figi("FUTNASD06240").build();
                OrderDomainEntity orderUsd = OrderDomainEntity.builder().figi("FUTUSDRUBF00").build();
                list.add(orderNg);
                list.add(orderNg2);
                list.add(orderSi);
                list.add(orderSl);
                list.add(orderBr);
                list.add(orderGo);
                list.add(orderGoG);
                list.add(orderNa);
                list.add(orderUsd);
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
        moexScrapingTask.isEnabled = true;
    }

    //@Test
    @ExtendWith(OutputCaptureExtension.class)
    public void webScrapingDerivativeUsdRatesTest(CapturedOutput capture) {
        moexScrapingTask.getDerivativeUsdRates();
        assertTrue(capture.getOut().contains("INFO"));
        assertFalse(capture.getOut().contains("ERROR"));
    }

    //@Test
    @ExtendWith(OutputCaptureExtension.class)
    public void webScrapingContractResultsTest(CapturedOutput capture) {
        moexScrapingTask.getContractResults();
        assertTrue(capture.getOut().contains("INFO"));
        assertFalse(capture.getOut().contains("ERROR"));
    }

    //@Test
    @ExtendWith(OutputCaptureExtension.class)
    public void webScrapingDayContractResultsTest(CapturedOutput capture) {
        moexScrapingTask.getDayContractResults();
        assertTrue(capture.getOut().contains("INFO"));
        assertFalse(capture.getOut().contains("ERROR"));
    }
}
