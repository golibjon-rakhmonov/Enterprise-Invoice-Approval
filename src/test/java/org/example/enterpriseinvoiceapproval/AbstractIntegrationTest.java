package org.example.enterpriseinvoiceapproval;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@org.springframework.test.context.ActiveProfiles("test")
public class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    static final GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
            .withCommand("server /data")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withExposedPorts(9000);

    static {
        postgres.start();
        minio.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("application.minio.url",
                () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));

        registry.add("application.minio.access-key", () -> "minioadmin");
        registry.add("application.minio.secret-key", () -> "minioadmin");
        registry.add("application.minio.bucket", () -> "test-invoices");
    }
}
