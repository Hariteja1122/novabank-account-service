package com.novabank.account.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Db2Container;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;

/**
 * Base for integration tests that need a real IBM Db2. One container is started per JVM
 * (Db2 takes minutes to initialize) and reused by every subclass. It loads the same
 * scripts as production, from novabank-platform/db2/schema, so the tests also prove that
 * the entities match the schema and that the sample data still loads.
 *
 * <p>The schema directory defaults to the sibling repo and can be overridden with
 * {@code -Dnovabank.schema.dir=...}.
 */
@SpringBootTest
public abstract class Db2IntegrationTest {

    protected static final String INTERNAL_API_KEY = "it-internal-key";

    private static final DockerImageName DB2_IMAGE = DockerImageName.parse("icr.io/db2_community/db2:11.5.9.0");

    protected static final Db2Container DB2 = new Db2Container(DB2_IMAGE)
            .acceptLicense()
            .withDatabaseName("NOVABANK")
            .withUsername("db2inst1")
            .withPassword("NovaBankIT2026")
            // First start creates the instance and database; slower under amd64 emulation on Apple silicon.
            .withStartupTimeout(Duration.ofMinutes(15));

    static {
        Path schemaDir = Path.of(System.getProperty("novabank.schema.dir", "../novabank-platform/db2/schema"));
        if (!Files.isRegularFile(schemaDir.resolve("01-schema.sql"))) {
            throw new IllegalStateException("Db2 schema scripts not found in " + schemaDir.toAbsolutePath()
                    + ". Check out novabank-platform next to this repo or pass -Dnovabank.schema.dir=<path>.");
        }
        DB2.start();
        try (Connection c = DriverManager.getConnection(jdbcUrl(), DB2.getUsername(), DB2.getPassword())) {
            ScriptUtils.executeSqlScript(c, new FileSystemResource(schemaDir.resolve("01-schema.sql")));
            ScriptUtils.executeSqlScript(c, new FileSystemResource(schemaDir.resolve("02-sample-data.sql")));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load the NovaBank schema into Db2", ex);
        }
    }

    private static String jdbcUrl() {
        // Same driver option as application.yml: readable SQL error messages.
        return DB2.getJdbcUrl() + ":retrieveMessagesFromServerOnGetMessage=true;";
    }

    @DynamicPropertySource
    static void db2Properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", Db2IntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", DB2::getUsername);
        registry.add("spring.datasource.password", DB2::getPassword);
        // Enough connections for the concurrency tests to contend on row locks, not on the pool.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
        registry.add("novabank.security.internal-api-key", () -> INTERNAL_API_KEY);
    }
}
