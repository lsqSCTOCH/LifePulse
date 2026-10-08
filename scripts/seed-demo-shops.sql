-- 非破坏性升级：保留现有表及商户，增加城市列。
SET @city_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_shop' AND column_name='city');
SET @city_ddl = IF(@city_exists=0, 'ALTER TABLE tb_shop ADD COLUMN city VARCHAR(64) NOT NULL DEFAULT ''杭州'' COMMENT ''商户所属城市''', 'SELECT 1');
PREPARE city_statement FROM @city_ddl;
EXECUTE city_statement;
DEALLOCATE PREPARE city_statement;

INSERT INTO tb_shop (name,type_id,images,city,area,address,x,y,avg_price,sold,comments,score,open_hours)
SELECT CONCAT('【演示】',t.name,' · 街角生活馆'),t.id,CONCAT('/imgs/demo/shop-',t.id,'.svg'),'演示','演示商圈','演示地址 · 不对应真实经营场所',0,0,0,0,0,0,'演示数据，无实际营业时间'
FROM tb_shop_type t WHERE NOT EXISTS (SELECT 1 FROM tb_shop s WHERE s.type_id=t.id AND s.name=CONCAT('【演示】',t.name,' · 街角生活馆'));

INSERT INTO tb_shop (name,type_id,images,city,area,address,x,y,avg_price,sold,comments,score,open_hours)
SELECT CONCAT('【演示】',t.name,' · 日光精选馆'),t.id,CONCAT('/imgs/demo/shop-',t.id,'.svg'),'演示','演示商圈','演示地址 · 不对应真实经营场所',0,0,0,0,0,0,'演示数据，无实际营业时间'
FROM tb_shop_type t WHERE NOT EXISTS (SELECT 1 FROM tb_shop s WHERE s.type_id=t.id AND s.name=CONCAT('【演示】',t.name,' · 日光精选馆'));

INSERT INTO tb_shop (name,type_id,images,city,area,address,x,y,avg_price,sold,comments,score,open_hours)
SELECT CONCAT('【演示】',t.name,' · 周末体验馆'),t.id,CONCAT('/imgs/demo/shop-',t.id,'.svg'),'演示','演示商圈','演示地址 · 不对应真实经营场所',0,0,0,0,0,0,'演示数据，无实际营业时间'
FROM tb_shop_type t WHERE NOT EXISTS (SELECT 1 FROM tb_shop s WHERE s.type_id=t.id AND s.name=CONCAT('【演示】',t.name,' · 周末体验馆'));
