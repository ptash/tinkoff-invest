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
        //FIGIES.put("BBG004731032", 1); // LKOH ЛУКОЙЛ
        //FIGIES.put("BBG004730JJ5", 1); // Московская Биржа
        //FIGIES.put("BBG002GHV6L9", 1); // SPBE
        //FIGIES.put("TCS00A103X66", 1); // POSI
        //FIGIES.put("BBG004731032", 1); // ЛУКОЙЛ
        //FIGIES.put("BBG002458LF8", 1); // SELG Селигдар
        //FIGIES.put("BBG004730RP0", 1); // Газпром
        //FIGIES.put("BBG002W2FT69", 1); // АбрауДюрсо
        //FIGIES.put("BBG000LNHHJ9", 1); // КАМАЗ
        //FIGIES.put("BBG004S68BH6", 1); // ПИК
        //FIGIES.put("BBG004TC84Z8", 1); // Трубная Металлургическая Компания
        //FIGIES.put("BBG00Y3XYV94", 1); // MDMG Мать и дитя
        //FIGIES.put("BBG004PYF2N3", 1); // POLY Polymetal
        //FIGIES.put("BBG00475K2X9", 1000); // HYDR РусГидро
        FIGIES.put("BBG004730ZJ9", 10000); // VTBR Банк ВТБ
        //FIGIES.put("BBG004S68B31", 10); // ALRS АЛРОСА
        //FIGIES.put("BBG000R607Y3", 1); // PLZL Полюс
        // instrument BBG004731354 (ROSN): InstrumentService.Instrument(figi=BBG004731354, tiket=ROSN, currency=rub, name=Роснефть, lot=1, minPriceIncrement=0.05000000, isBuyAvailable=true)
        // instrument BBG004S68507 (MAGN): InstrumentService.Instrument(figi=BBG004S68507, tiket=MAGN, currency=rub, name=Магнитогорский металлургический комбинат, lot=10, minPriceIncrement=0.00500000, isBuyAvailable=true)
        // instrument BBG000PZ0833 (MGTSP): InstrumentService.Instrument(figi=BBG000PZ0833, tiket=MGTSP, currency=rub, name=МГТС - акции привилегированные, lot=1, minPriceIncrement=2.00000000, isBuyAvailable=true)
        // instrument BBG0100R9963 (SGZH): InstrumentService.Instrument(figi=BBG0100R9963, tiket=SGZH, currency=rub, name=Сегежа, lot=100, minPriceIncrement=0.00100000, isBuyAvailable=true)
        // instrument BBG004S68DD6 (MSTT): InstrumentService.Instrument(figi=BBG004S68DD6, tiket=MSTT, currency=rub, name=Мостотрест, lot=10, minPriceIncrement=0.05000000, isBuyAvailable=true)
        // instrument BBG000R04X57 (FLOT): InstrumentService.Instrument(figi=BBG000R04X57, tiket=FLOT, currency=rub, name=Совкомфлот, lot=10, minPriceIncrement=0.01000000, isBuyAvailable=true)
        // instrument BBG000BBV4M5 (CNTL): InstrumentService.Instrument(figi=BBG000BBV4M5, tiket=CNTL, currency=rub, name=Центральный Телеграф, lot=100, minPriceIncrement=0.02000000, isBuyAvailable=true)
        // instrument BBG004S689R0 (PHOR): InstrumentService.Instrument(figi=BBG004S689R0, tiket=PHOR, currency=rub, name=ФосАгро, lot=1, minPriceIncrement=1.00000000, isBuyAvailable=true)
        // instrument BBG000RTHVK7 (GCHE): InstrumentService.Instrument(figi=BBG000RTHVK7, tiket=GCHE, currency=rub, name=Группа Черкизово, lot=1, minPriceIncrement=0.50000000, isBuyAvailable=true)
        //
        //FIGIES.put("BBG00Y91R9T3", 1); // OZON
        //FIGIES.put("BBG0029SG1C1", 10); // KZOSP ПАО «КАЗАНЬОРГСИНТЕЗ» - акции привилегированные
        //FIGIES.put("BBG222222222", 100); // Тинькофф Золото
        //FIGIES.put("BBG000NLB2G3", 10); // KROT Красный Октябрь
        //FIGIES.put("BBG005DXJS36", 1); // TCS Group (Tinkoff Bank holder)
        //FIGIES.put("BBG00KHGQ0H4", 1); // HHR HeadHunter Group PLC
        // HKD hkd
        //FIGIES.put("BBG00QV37ZP9", 10); // 9988 Alibaba
        //FIGIES.put("BBG0120WC125", 10); // 2015 Li Auto
        //FIGIES.put("BBG000BFNTD0", 100); // 857 PetroChina
        //FIGIES.put("BBG00KVTBY91", 100); // 1810 Xiaomi
        //FIGIES.put("BBG000BZ3PX4", 100); // 175 Geely Automobile Holdings
        //FIGIES.put("BBG00ZNW65W3", 10); // 9626 Bilibili // 20
        FIGIES.put("BBG00VC6RYV6", 10); // 9618 JD.com // 50, 100
        FIGIES.put("BBG000CN0Y73", 100); // 2600 Aluminum Corp of China // 50, 100
        //FIGIES.put("BBG000BG4QM5", 100); // 992 Lenovo // candles 1hour figi BBG000BG4QM5 from 2021-01-24T01:59+03:00 to 2022-12-21T17:59+03:00. Expect length 2000, real 497, total 740, begin 2022-10-12T08:00+03:00, end 2023-02-02T10:00+03:00
        //FIGIES.put("BBG00V6PS1F0", 10); // 9999 NetEase
        //FIGIES.put("BBG00ZMFX1S5", 10); // 9888 Baidu
        //FIGIES.put("BBG0078Q6BC4", 100); // 1347 Hua Hong Semiconductor // candles 1hour figi BBG0078Q6BC4 from 2022-07-11T09:59+03:00 to 2022-12-21T17:59+03:00. Expect length 400, real 337, total 573, begin 2022-10-12T08:00+03:00, end 2023-02-02T10:00+03:00
        //FIGIES.put("BBG000C1DC75", 100); // 836 China Resources Power // candles 1hour figi BBG000C1DC75 from 2022-07-11T09:59+03:00 to 2022-12-21T17:59+03:00. Expect length 400, real 291, total 504, begin 2022-10-13T08:00+03:00, end 2023-02-02T10:00+03:00
        FIGIES.put("BBG000C6XDL4", 100); // 1177 Sino Biopharmaceutical // 50, 100
    }
    @Override
    public Map<String, Integer> getFigies()  { return FIGIES; }

    public boolean isEnabled() {
        return true;
    }
}
