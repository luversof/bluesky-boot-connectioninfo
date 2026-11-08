package io.github.luversof.boot.connectioninfo.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.github.luversof.boot.autoconfigure.connectioninfo.ConnectionInfoAutoConfiguration;
import io.github.luversof.boot.autoconfigure.connectioninfo.jdbc.ConnectionInfoJdbcAutoConfiguration;

/** createHikariConfig가 package-private이라 같은 패키지에 둔다. */
class HikariDataSourceConnectionInfoLoaderTests {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConnectionInfoAutoConfiguration.class, ConnectionInfoJdbcAutoConfiguration.class))
          .withPropertyValues("bluesky-boot.connection-info.loaders.hikaridatasource.enabled=true");

  @Test
  void appliesLoaderPropertiesToHikariConfig() {
    this.contextRunner
        .withPropertyValues(
            "bluesky-boot.connection-info.loaders.hikaridatasource.properties.maximumPoolSize=5",
            "bluesky-boot.connection-info.loaders.hikaridatasource.properties.minimumIdle=1")
        .run(context -> {
          var config =
              context.getBean(HikariDataSourceConnectionInfoLoader.class).createHikariConfig();
          assertThat(config.getMaximumPoolSize()).isEqualTo(5);
          assertThat(config.getMinimumIdle()).isEqualTo(1);
        });
  }
}
