-- 약국 대표 사진 URL. 현재 공공데이터 소스(pharmacy_raw.json, 심평원 xlsx)에는 사진이 없어 항상 NULL —
-- 나중에 실제 사진 소스가 생기면 이 컬럼만 채우면 된다.
ALTER TABLE pharmacy ADD COLUMN photo_url VARCHAR(500);
