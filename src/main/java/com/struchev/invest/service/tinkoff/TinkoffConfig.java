package com.struchev.invest.service.tinkoff;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "tinkoff")
@Data
public class TinkoffConfig {
    private Map<String, String> accountIdByCurrency = new HashMap<>();
}
