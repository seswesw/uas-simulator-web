package ru.kirsachik.uas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

/**
 * Добавляет столбцы в существующую H2-базу после обновления модели Drone.
 */
@Component
@Profile("h2")
@Order(0)
public class DatabaseSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaMigration.class);

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public DatabaseSchemaMigration(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        patchDronesTable();
    }

    private void patchDronesTable() {
        addColumn("drones", "source_type", "VARCHAR(32) DEFAULT 'SIMULATOR' NOT NULL");
        addColumn("drones", "connection_protocol", "VARCHAR(32) DEFAULT 'NONE' NOT NULL");
        addColumn("drones", "endpoint", "VARCHAR(512)");
        addColumn("drones", "api_key", "VARCHAR(64)");
        addColumn("drones", "external_device_id", "VARCHAR(128)");
        addColumn("drones", "connected", "BOOLEAN DEFAULT FALSE NOT NULL");
        addColumn("drones", "last_telemetry_at", "TIMESTAMP");
    }

    private void addColumn(String table, String column, String definition) {
        if (columnExists(table, column)) {
            return;
        }
        String sql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
        log.info("Миграция БД: {}", sql);
        jdbc.execute(sql);
    }

    private boolean columnExists(String table, String column) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String catalog = connection.getCatalog();
            try (ResultSet rs = meta.getColumns(catalog, null, table.toUpperCase(), column.toUpperCase())) {
                if (rs.next()) {
                    return true;
                }
            }
            try (ResultSet rs = meta.getColumns(catalog, null, table, column)) {
                return rs.next();
            }
        } catch (Exception e) {
            log.warn("Проверка столбца {}.{}: {}", table, column, e.getMessage());
            return false;
        }
    }
}
