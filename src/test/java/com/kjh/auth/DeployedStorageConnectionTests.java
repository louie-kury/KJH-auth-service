package com.kjh.auth;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AUTH_STORAGE_CONNECTION_TEST", matches = "true")
@SpringBootTest(classes = DeployedStorageConnectionTests.ConnectionConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.flyway.enabled=false", "spring.sql.init.mode=never"})
class DeployedStorageConnectionTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisConnectionFactory redisConnections;

    @Test
    void connectsToMysqlUsingApplicationConfiguration() throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT 1, VERSION()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
            assertThat(result.getString(2)).isEqualTo("8.4.13");
        }
    }

    @Test
    void connectsToRedisUsingApplicationConfiguration() {
        try (var connection = redisConnections.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
            assertThat(connection.serverCommands().info("server").getProperty("redis_version"))
                    .isEqualTo("8.2.11");
        }
    }

    // Load Boot's real connection beans without starting email delivery or application workflows.
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConnectionConfiguration {
    }
}
