package com.bank.db;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Provides JDBC connections. Env vars override db.properties. */
public final class Database {
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = Database.class.getResourceAsStream("/db.properties")) {
            if (in != null) PROPS.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Database() {}

    private static String setting(String env, String key) {
        String v = System.getenv(env);
        return (v != null && !v.isBlank()) ? v : PROPS.getProperty(key);
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                setting("BANK_DB_URL", "db.url"),
                setting("BANK_DB_USER", "db.user"),
                setting("BANK_DB_PASSWORD", "db.password"));
    }
}
