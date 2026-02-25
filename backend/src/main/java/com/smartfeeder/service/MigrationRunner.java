package com.smartfeeder.service;

import com.smartfeeder.dao.DbClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;

@Component
@Root
public final class MigrationRunner {
    private static final Logger logger = LoggerFactory.getLogger(MigrationRunner.class);

    public MigrationRunner(DbClient dbClient) {
        var flyway = Flyway.configure()
            .dataSource(dbClient.dataSource())
            .locations("classpath:db/migration")
            .load();

        int executed = 0;
        try {
            var result = flyway.migrate();
            executed = result.migrationsExecuted;
        } catch (Exception e) {
            logger.warn("Flyway migration execution failed, fallback SQL migrator will be used: {}", e.getMessage());
        }
        logger.info("Flyway migrations executed: {}", executed);

        if (!tableExists(dbClient, "users")) {
            logger.warn("Flyway didn't create required tables, applying SQL fallback migrations");
            runSqlScript(dbClient, "/db/migration/V1__init.sql");
            runSqlScript(dbClient, "/db/migration/V2__seed_base.sql");
        }
    }

    private boolean tableExists(DbClient dbClient, String tableName) {
        String sql = """
            SELECT EXISTS (
              SELECT 1
              FROM information_schema.tables
              WHERE table_schema = 'public' AND table_name = ?
            )
            """;
        try (Connection connection = dbClient.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, tableName);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot verify migration table existence", e);
        }
    }

    private void runSqlScript(DbClient dbClient, String resourcePath) {
        String script;
        try (var in = MigrationRunner.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration script: " + resourcePath);
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read migration script: " + resourcePath, e);
        }

        String cleaned = script.replaceAll("(?m)^\\s*--.*$", "");
        String[] statements = cleaned.split(";");

        try (Connection connection = dbClient.getConnection()) {
            connection.setAutoCommit(false);
            for (String raw : statements) {
                String sql = raw.trim();
                if (sql.isEmpty()) {
                    continue;
                }
                try (PreparedStatement st = connection.prepareStatement(sql)) {
                    st.execute();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot execute fallback migrations", e);
        }
    }
}
