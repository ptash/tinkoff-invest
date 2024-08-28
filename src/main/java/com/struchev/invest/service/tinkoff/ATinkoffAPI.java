package com.struchev.invest.service.tinkoff;

import com.struchev.invest.service.dictionary.InstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.tinkoff.piapi.contract.v1.AccountType;
import ru.tinkoff.piapi.core.InvestApi;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class ATinkoffAPI implements ITinkoffCommonAPI, ITinkoffOrderAPI {

    @Value("${tinkoff.token}")
    private String token;

    @Value("${tinkoff.account-id}")
    private String accountId;

    @Autowired
    private TinkoffConfig config;

    @Value("${tinkoff.is-token-sandbox:false}")
    private Boolean isSandboxMode;

    @Value("${tinkoff.net.debug:null}")
    private String netDebug;

    @Value("${tinkoff.feature.multiply:1}")
    private Integer featureMultiply;

    private InvestApi api;

    @Override
    public InvestApi getApi() {
        return api;
    }

    @Override
    public String getAccountId() {
        return accountId;
    }

    public String getAccountIdByFigi(InstrumentService.Instrument instrument)
    {
        if (null == config.getAccountIdByCurrency() || !config.getAccountIdByCurrency().containsKey(instrument.getCurrency().toUpperCase())) {
            return accountId;
        }
        return config.getAccountIdByCurrency().get(instrument.getCurrency().toUpperCase());
    }

    @Override
    public boolean getIsSandboxMode() {
        return isSandboxMode;
    }

    public Integer getFeatureMultiply() {
        return featureMultiply;
    }

    @PostConstruct
    private void init() {
        //System.setProperty("javax.net.debug", "ssl:handshake");
        if (netDebug != null && !netDebug.isEmpty()) {
            System.setProperty("javax.net.debug", netDebug);
        }
        if (null == token || token.isEmpty()) {
            throw new RuntimeException("Token is empty");
        }
        log.info("Current token: {}***", token.substring(0, 5));
        if (isSandboxMode) {
            log.info("Sandbox Mode");
        }
        api = isSandboxMode ? InvestApi.createSandbox(token, "roman-struchev") : InvestApi.create(token, "roman-struchev");

        // Проверяем, что аккаунт существует (если задан в конфигах) или выбираем первый
        var accounts = isSandboxMode
                ? api.getSandboxService().getAccountsSync() : api.getUserService().getAccountsSync();
        log.info("Available accounts: {}, need = {}", accounts.size(), StringUtils.isEmpty(accountId) ? "first" : accountId);
        if (isSandboxMode && accounts.size() == 0) {
            String newAccount = api.getSandboxService().openAccountSync();
            accounts = api.getSandboxService().getAccountsSync();
            log.info("Available accounts: {}, need = {}", accounts.size(), StringUtils.isEmpty(accountId) ? "first" : accountId);
        }
        accounts.forEach(a -> log.info("Account id {}, name {}", a.getId(), a.getName()));
        var account = accounts.stream()
                .filter(a -> a.getType() == AccountType.ACCOUNT_TYPE_TINKOFF)
                .filter(a -> StringUtils.isEmpty(accountId) || accountId.equals(a.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Account was not found for token " + token));
        log.info("Will use Account id {}, name {}", account.getId(), account.getName());
        accountId = account.getId();
        config.getAccountIdByCurrency().forEach((k, v) -> log.info("Account by {}: {}", k, v));
    }
}
