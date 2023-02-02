package com.struchev.invest.strategy.instrument_by_fiat_factorial;

import com.struchev.invest.strategy.instrument_by_fiat_cross.AInstrumentByFiatCrossStrategy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FactorialStrategy extends AInstrumentByFiatFactorialStrategy {
    private static final Map FIGIES = new HashMap<String, Integer>();
    static {
        //FIGIES.put("BBG000LWVHN8", 1); // Дагестанская энергосбытовая компания
        //FIGIES.put("BBG004S681W1", 10); // МТС
        //FIGIES.put("BBG00475KKY8", 1); // НОВАТЭК
        //FIGIES.put("BBG006L8G4H1", 1); // Yandex
        //FIGIES.put("BBG00178PGX3", 1); // VK
        //FIGIES.put("BBG00QPYJ5H0", 1); // TCS Group
        //FIGIES.put("BBG004730N88", 10); // SBER
        //FIGIES.put("BBG004730RP0", 1); // Газпром
        FIGIES.put("BBG004731032", 1); // LKOH ЛУКОЙЛ
        //FIGIES.put("BBG004730JJ5", 1); // Московская Биржа
        //FIGIES.put("BBG002GHV6L9", 1); // SPBE
        //FIGIES.put("TCS00A103X66", 1); // POSI
        //FIGIES.put("BBG004731032", 1); // ЛУКОЙЛ
        //FIGIES.put("BBG002458LF8", 1); // SELG Селигдар
        //FIGIES.put("BBG004730RP0", 1); // Газпром
        //FIGIES.put("BBG00Y91R9T3", 1); // OZON
        //FIGIES.put("BBG0029SG1C1", 10); // KZOSP ПАО «КАЗАНЬОРГСИНТЕЗ» - акции привилегированные
        //FIGIES.put("BBG222222222", 100); // Тинькофф Золото
        FIGIES.put("BBG005DXJS36", 1); // TCS Group (Tinkoff Bank holder)
        // HKD hkd
        //FIGIES.put("BBG00QV37ZP9", 1); // 9988 Alibaba
        //FIGIES.put("BBG0120WC125", 1); // 2015 Li Auto
        //FIGIES.put("BBG000BFNTD0", 1); // 857 PetroChina
        //FIGIES.put("BBG00KVTBY91", 1); // 1810 Xiaomi
        //FIGIES.put("BBG000BZ3PX4", 1); // 175 Geely Automobile Holdings
        FIGIES.put("BBG00ZNW65W3", 1); // 9626 Bilibili // candles 1hour figi BBG00ZNW65W3 from 2021-01-24T01:59+03:00 to 2022-12-21T17:59+03:00. Expect length 2000, real 494, total 735, begin 2022-10-12T08:00+03:00, end 2023-02-02T10:00+03:00
        FIGIES.put("BBG00VC6RYV6", 10); // 9618 JD.com
        FIGIES.put("BBG000CN0Y73", 100); // 2600 Aluminum Corp of China
        FIGIES.put("BBG000BG4QM5", 100); // 992 Lenovo // candles 1hour figi BBG000BG4QM5 from 2021-01-24T01:59+03:00 to 2022-12-21T17:59+03:00. Expect length 2000, real 497, total 740, begin 2022-10-12T08:00+03:00, end 2023-02-02T10:00+03:00
        FIGIES.put("BBG00V6PS1F0", 10); // 9999 NetEase
        //FIGIES.put("BBG00ZMFX1S5", 10); // 9888 Baidu
        //FIGIES.put("BBG0078Q6BC4", 100); // 1347 Hua Hong Semiconductor
        //FIGIES.put("BBG000C1DC75", 100); // 836 China Resources Power
        //FIGIES.put("BBG000C6XDL4", 100); // 1177 Sino Biopharmaceutical
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }

    public boolean isEnabled() {
        return true;
    }
}
