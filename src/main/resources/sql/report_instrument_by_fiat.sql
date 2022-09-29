select a.figi_title                                                   figi,
       a.strategy                                                     strategy,
       coalesce(round(100 * a.total / (a.first_price * a.lots), 2), -555)             profit_by_robot,
       coalesce(round(100 * (a.last_price - a.first_price) / a.first_price, 2), -555) profit_by_invest,
       a.offers                                                       offers,
       a.first_price                                                  first_price,
       coalesce(a.last_price, -555)                                   last_price
        , (select min(purchase_date_time) FROM offer where figi = a.figi AND strategy=a.strategy) AS start_date
        , (select max(sell_date_time) FROM offer where figi = a.figi AND strategy=a.strategy) AS stop_date
from (select o.figi_title,
             o.figi,
             o.strategy,
             o.lots,
             count(o.*)                                                                                     offers,
             sum((o.sell_price - o.purchase_price) * o.lots - o.purchase_commission - o.sell_commission)    total,
             (select c.closing_price from candle c where c.figi = o.figi
                                                     AND c.date_time <= (select max(sell_date_time) FROM offer where figi = o.figi AND strategy=o.strategy)
                                                     AND c.date_time >= (select min(sell_date_time) FROM offer  where figi = o.figi AND strategy=o.strategy)
              order by c.date_time desc limit 1) last_price,
             (select oi.purchase_price from offer oi where oi.figi = o.figi AND oi.strategy = o.strategy order by oi.id limit 1)         first_price
      from offer o
      group by figi_title, figi, strategy, lots) a
order by figi_title, profit_by_robot desc, strategy