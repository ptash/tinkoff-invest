ALTER TABLE IF EXISTS public.offer
    ALTER COLUMN purchase_commission DROP NOT NULL;

ALTER TABLE IF EXISTS public.offer
    ALTER COLUMN purchase_date_time DROP NOT NULL;

ALTER TABLE IF EXISTS public.offer
    ALTER COLUMN purchase_price DROP NOT NULL;