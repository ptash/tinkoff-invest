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

        //FIGIES.put("FUTNG0323000", 1); // NGH3 NG-3.23 Природный газ
        //FIGIES.put("FUTNG0423001", 1); // NGJ3 NG-4.23 Природный газ
        //FIGIES.put("FUTSI1223000", 1); // SiZ3 Si-12.23 Курс доллар - рубль
        //FIGIES.put("FUTSI0323000", 1); // SiH3 Si-3.23 Курс доллар - рубль
        //FIGIES.put("FUTSI0623000", 1); // SiM3 Si-6.23 Курс доллар - рубль
        //FIGIES.put("FUTRTS032300", 1); // RIH3 RTS-3.23 Индекс РТС
        //FIGIES.put("FUTSILV09230", 1); // SVU3 SILV-9.23 Серебро
        //FIGIES.put("FUTSILV06230", 1); // SVM3 SILV-6.23 Серебро
        //FIGIES.put("FUTBR0423000", 1); // BRJ3 BR-4.23 Нефть Brent
        FIGIES.put("FUTMXI032300", 1); // MMH3 MXI-3.23 Индекс МосБиржи (мини)
        //FIGIES.put("FUTNASD03230", 1); // NAH3 NASD-3.23 Nasdaq 100
        //FIGIES.put("FUTNASD06230", 1); // NAM3 NASD-6.23 Nasdaq 100
        //FIGIES.put("FUTGOLD03230", 1); // GDH3 GOLD-3.23 Золото
        //FIGIES.put("FUTGOLD06230", 1); // GDM3 GOLD-6.23 Золото
        //FIGIES.put("FUTCNY032300", 1); // CRH3 GCNY-3.23 Курс Юань - Рубль
        //FIGIES.put("FUTCNY062300", 1); // CRM3 CNY-6.23 Курс Юань - Рубль

        //FIGIES.put("BBG00178PGX3", 1); // VK
        //FIGIES.put("BBG00QPYJ5H0", 1); // TCS Group
        //FIGIES.put("BBG004730N88", 10); // SBER
        //FIGIES.put("BBG000QJW156", 10); // BSPB Банк Санкт-Петербург
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
        //FIGIES.put("BBG004730ZJ9", 10000); // VTBR Банк ВТБ
        //FIGIES.put("BBG004S68B31", 10); // ALRS АЛРОСА
        //FIGIES.put("BBG000R607Y3", 1); // PLZL Полюс
        //FIGIES.put("BBG004731354", 1); // ROSN Роснефть
        //FIGIES.put("BBG004S68507", 10); // MAGN Магнитогорский металлургический комбинат
        //FIGIES.put("BBG000PZ0833", 1); // MGTSP МГТС - акции привилегированные
        //FIGIES.put("BBG0100R9963", 100); // SGZH Сегежа // candles 1hour figi BBG0100R9963 from 2018-12-30T10:59+03:00 to 2022-09-23T18:59+03:00. Expect length 4000, real 3117, total 4290, begin 2021-04-28T10:00+03:00, end 2023-02-08T14:00+03:00
        //FIGIES.put("BBG004S68DD6", 10); // MSTT Мостотрест
        //FIGIES.put("BBG000R04X57", 10); // FLOT Совкомфлот
        //FIGIES.put("BBG000BBV4M5", 100); // CNTL Центральный Телеграф
        //FIGIES.put("BBG004S689R0", 1); // PHOR ФосАгро
        //FIGIES.put("BBG000RTHVK7", 1); // GCHE Группа Черкизово
        //FIGIES.put("BBG000VG1034", 10000); // MRKP Россети Центр и Приволжье
        //FIGIES.put("BBG00JXPFBN0", 1); // FIVE ГДР X5 RetailGroup
        //FIGIES.put("BBG004S68696", 1); // Распадская
        //FIGIES.put("BBG000QFH687", 1); // ТГК-1
        //FIGIES.put("BBG004S683W7", 1); // Аэрофлот
        //FIGIES.put("BBG012YQ6P43", 1); // CIAN АДР Циан // candles 1hour figi BBG012YQ6P43 from 2018-12-02T10:59+03:00 to 2022-08-26T18:59+03:00. Expect length 4000, real 2004, total 3776, begin 2021-11-05T20:00+03:00, end 2023-02-08T23:00+03:00
        //FIGIES.put("BBG001BBGNS2", 10); // ORUP Обувь России
        //FIGIES.put("BBG000K3STR7", 10); // APTK Аптечная сеть 36,6
        //FIGIES.put("BBG0019K04R5", 100); // LIFE Фармсинтез
        //FIGIES.put("BBG003BNWBP3", 100); // PRFN ЧЗПСН
        //FIGIES.put("BBG000RJWGC4", 100); // AMEZ Ашинский метзавод
        //FIGIES.put("BBG000VQWH86", 100); // BLNG Белон
        //FIGIES.put("BBG000TY1CD1", 100); // BELU Белуга Групп ПАО ао
        //FIGIES.put("BBG000RP8V70", 1); // CHMK ЧМК
        //
        //FIGIES.put("BBG00Y91R9T3", 1); // OZON
        //FIGIES.put("BBG0029SG1C1", 10); // KZOSP ПАО «КАЗАНЬОРГСИНТЕЗ» - акции привилегированные
        //FIGIES.put("BBG222222222", 100); // Тинькофф Золото
        //FIGIES.put("BBG000NLB2G3", 10); // KROT Красный Октябрь
        //FIGIES.put("BBG000N16BP3", 10); // ISKJ ИСКЧ
        //FIGIES.put("BBG005DXJS36", 1); // TCS Group (Tinkoff Bank holder)
        FIGIES.put("BBG00KHGQ0H4", 1); // HHR HeadHunter Group PLC
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
        return false;
    }
}
