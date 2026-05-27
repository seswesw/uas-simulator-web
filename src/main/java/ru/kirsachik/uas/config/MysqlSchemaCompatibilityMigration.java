package ru.kirsachik.uas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Совместимость с MySQL-базой, которая могла быть создана старой версией проекта.
 * В старой схеме Hibernate мог создать ошибочные столбцы altitudem/max_altitudem.
 */
@Component
@Profile("mysql")
@Order(0)
public class MysqlSchemaCompatibilityMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MysqlSchemaCompatibilityMigration.class);

    private final JdbcTemplate jdbc;

    public MysqlSchemaCompatibilityMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        dropColumnIfExists("drones", "altitudem");
        dropColumnIfExists("drones", "max_altitudem");
        dropColumnIfExists("waypoints", "altitudem");
        dropColumnIfExists("telemetry_records", "altitudem");
    }

    private void dropColumnIfExists(String tableName, String columnName) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);

        if (count != null && count > 0) {
            String sql = "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
            log.info("Очистка MySQL-схемы: {}", sql);
            jdbc.execute(sql);
        }
    }
}
