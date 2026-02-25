package com.smartfeeder.dao;

import com.smartfeeder.config.DbConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import ru.tinkoff.kora.common.Component;

@Component
public final class DbClient implements AutoCloseable {
    private final HikariDataSource dataSource;

    public DbClient(DbConfig dbConfig) {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(dbConfig.jdbcUrl());
        cfg.setUsername(dbConfig.username());
        cfg.setPassword(dbConfig.password());
        cfg.setMaximumPoolSize(dbConfig.maxPoolSize());
        cfg.setPoolName(dbConfig.poolName());
        cfg.setAutoCommit(true);
        this.dataSource = new HikariDataSource(cfg);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
