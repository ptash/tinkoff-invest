package com.struchev.invest.repository;

import com.struchev.invest.entity.CurrencyRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

public interface CurrencyRateRepository extends JpaRepository<CurrencyRateEntity, Long> {
    CurrencyRateEntity findByFromCurrencyAndToCurrencyAndDateTime(String fromCurrency, String toCurrency, OffsetDateTime dateTime);
}
