package com.hmdp.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 非破坏性本地项目升级：保留已有表和数据，只补充城市列。 */
@Component
@Order(-100)
public class ShopCityMigration implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    public ShopCityMigration(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public void run(String... args) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema=DATABASE() AND table_name='tb_shop' AND column_name='city'", Integer.class);
        String[][] columns = {{"catalog_key", "VARCHAR(64)"}, {"campus", "VARCHAR(64)"},
                {"source_url", "TEXT"}, {"rating_source", "VARCHAR(64)"}, {"verified_at", "VARCHAR(32)"},
                {"photo_source_url", "TEXT"}, {"catalog_note", "TEXT"}};
        for (String[] definition : columns) {
            String column = definition[0];
            java.util.List<String> present = jdbc.queryForList("SELECT column_type FROM information_schema.columns " +
                    "WHERE table_schema=DATABASE() AND table_name='tb_shop' AND column_name=?", String.class, column);
            if (present.isEmpty()) {
                jdbc.execute("ALTER TABLE tb_shop ADD COLUMN " + column + " " + definition[1] + " NULL");
            } else if ("varchar(1024)".equalsIgnoreCase(present.get(0))) {
                jdbc.execute("ALTER TABLE tb_shop MODIFY COLUMN " + column + " " + definition[1] + " NULL");
            }
        }
        if (exists != null && exists == 0) {
            jdbc.execute("ALTER TABLE tb_shop ADD COLUMN city VARCHAR(64) NOT NULL DEFAULT '杭州' COMMENT '商户所属城市'");
        }
    }
}
