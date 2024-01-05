select a.figi_title                                                   figi_title,
		a.figi,
       a.strategy                                                     strategy,
       round(100 * a.total / (a.first_price * a.lots), 2)             profit_by_robot,
       round(100 * (a.last_price - a.first_price) / a.first_price, 2) profit_by_invest,
       a.offers                                                       offers,
       a.first_price                                                  first_price,
       a.last_price                                                   last_price
, (select min(purchase_date_time) FROM offer where figi = a.figi) AS start_date
, (select max(sell_date_time) FROM offer where figi = a.figi) AS stop_date
from (select o.figi_title,
	         o.figi,
             o.strategy,
             o.lots,
             count(o.*)                                                                                     offers,
             sum((o.sell_price - o.purchase_price) * o.lots - o.purchase_commission - o.sell_commission)    total,
             (select c.closing_price from candle c where c.figi = o.figi
			  AND c.date_time <= (select max(sell_date_time) FROM offer)
			  AND c.date_time >= (select min(sell_date_time) FROM offer)
			  order by c.date_time desc limit 1) last_price,
             (select oi.purchase_price from offer oi where oi.figi = o.figi
			  order by oi.id limit 1)         first_price
      from offer o
      group by figi_title, figi, strategy, lots) a
order by figi_title, profit_by_robot desc, strategy


select a.figi_title                                                   figi_title,
       a.strategy                                                     strategy,
	   --a.lots,
	   round(a.total/a.lots, 2) as total,
	   round(a.total_prev/a.lots, 2) as total_with_comm,
	   round(a.first_price, 2) as first_price,
       coalesce(round(100 * a.total / (a.first_price * a.lots), 2), -555)             profit_by_robot,
	   coalesce(round(100 * a.total_prev / (a.first_price * a.lots), 2), -555)             profit_by_robot_prev,
       coalesce(round(100 * (a.last_price - a.first_price) / a.first_price, 2), -555) profit_by_invest,
       a.offers                                                       offers,
	   coalesce(round(100 * a.total_profit / (a.first_price * a.lots), 2), -555)             profit,
	   a.offers_profit,
	   coalesce(round(100 * a.total_loss / (a.first_price * a.lots), 2), -555)             loss,
	   a.offers_loss,
       round(a.first_price, 2)                                                  first_price,
       coalesce(round(a.last_price, 2), -555)                                   last_price
       , a.figi                                                       figi
        , (select min(purchase_date_time) FROM offer where figi = a.figi AND strategy=a.strategy) AS start_date
        , (select max(sell_date_time) FROM offer where figi = a.figi AND strategy=a.strategy) AS stop_date
from (select o.figi_title,
             o.figi,
             o.strategy,
             o.lots,
             count(o.*)                                                                                     offers,
             sum((o.sell_price - o.purchase_price) * o.lots /*- o.purchase_commission - o.sell_commission*/)    total,
	         sum((o.sell_price - o.purchase_price) * o.lots - o.purchase_commission - o.sell_commission)    total_prev,
	         sum(CASE WHEN o.sell_price > o.purchase_price THEN (o.sell_price - o.purchase_price) * o.lots - o.purchase_commission - o.sell_commission ELSE 0 END) total_profit,
	         sum(CASE WHEN o.sell_price > o.purchase_price THEN 1 ELSE 0 END) offers_profit,
             sum(CASE WHEN o.sell_price <= o.purchase_price THEN (o.sell_price - o.purchase_price) * o.lots - o.purchase_commission - o.sell_commission ELSE 0 END) total_loss,
	         sum(CASE WHEN o.sell_price <= o.purchase_price THEN 1 ELSE 0 END) offers_loss,
             (select c.closing_price from candle c where c.figi = o.figi
                                                     AND c.date_time <= (select max(sell_date_time) FROM offer where figi = o.figi AND strategy=o.strategy)
                                                     AND c.date_time >= (select min(sell_date_time) FROM offer  where figi = o.figi AND strategy=o.strategy)
              order by c.date_time desc limit 1) last_price,
             (select oi.purchase_price from offer oi where oi.figi = o.figi AND oi.strategy = o.strategy order by oi.id limit 1)         first_price
      from offer o
      group by figi_title, figi, strategy, lots) a
order by figi_title, strategy desc, profit_by_robot desc;

--=============
select date, diff, LAG(diff) OVER (),
SUM(diff) OVER (ORDER BY date ROWS UNBOUNDED PRECEDING) AS priceday,
35000 + (SUM(diff) OVER (ORDER BY date ROWS UNBOUNDED PRECEDING)) AS total
from (
	select
to_char(sell_date_time, 'YYYY/MM/DD') as date,
sum(coalesce(sell_price_money, sell_price) - coalesce(purchase_price_money, purchase_price) - purchase_commission - sell_commission) as diff
from offer where sell_date_time > '2023-03-02 07:01:00'
group by to_char(sell_date_time, 'YYYY/MM/DD')
) as t
order by date;