package io.github.luversof.boot.autoconfigure.connectioninfo.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.zaxxer.hikari.HikariDataSource;

import io.github.luversof.boot.autoconfigure.connectioninfo.ConnectionInfoAutoConfiguration;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.ConnectionInfoRegistry;
import io.github.luversof.boot.connectioninfo.DataSourceConnectionConfig;

/** alias마다 {alias}DataSource 빈이 등록되는지. DB 없이 풀을 만들도록 첫 연결 시도를 끈다. */
class ConnectionInfoDataSourceBeanRegistrarTests {

  private static final String PREFIX = "bluesky-boot.connection-info.loaders.hikaridatasource.";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConnectionInfoAutoConfiguration.class, ConnectionInfoJdbcAutoConfiguration.class))
          .withBean(FakeReader.class)
          .withPropertyValues(
              PREFIX + "enabled=true",
              PREFIX + "properties.initializationFailTimeout=-1",
              PREFIX + "properties.minimumIdle=0");

  @Test
  void registersDataSourceBeanNamedAfterAlias() {
    this.contextRunner
        .withPropertyValues(PREFIX + "connections.stock=db_stock")
        .run(context -> {
          assertThat(context).hasNotFailed();
          var dataSource = context.getBean("stockDataSource", HikariDataSource.class);
          @SuppressWarnings("unchecked")
          ConnectionInfoRegistry<HikariDataSource> registry =
              context.getBean(ConnectionInfoRegistry.class);
          assertThat(dataSource)
              .isSameAs(registry.getConnectionInfo("db_stock").getFirst().getConnection());
        });
  }

  @Test
  void skipsAliasWhoseConnectionIsNotLoaded() {
    // 메타 DB에 없는 connection이면 빈을 만들지 않고 기동은 계속한다
    this.contextRunner
        .withPropertyValues(
            PREFIX + "connections.stock=db_stock", PREFIX + "connections.missing=unknown")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBeansOfType(HikariDataSource.class))
              .containsOnlyKeys("stockDataSource");
        });
  }

  @Test
  void keepsExistingBeanWithSameName() {
    this.contextRunner
        .withPropertyValues(PREFIX + "connections.stock=db_stock")
        .withBean("stockDataSource", String.class, () -> "user bean")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean("stockDataSource")).isEqualTo("user bean");
        });
  }

  @Test
  void registersNothingWithoutLoader() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ConnectionInfoAutoConfiguration.class, ConnectionInfoJdbcAutoConfiguration.class))
        .withPropertyValues(PREFIX + "connections.stock=db_stock")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean("stockDataSource");
        });
  }

  /** 이름이 db_ 로 시작하는 connection만 아는 리더 */
  static class FakeReader implements ConnectionInfoReader<DataSourceConnectionConfig> {

    @Override
    public String getReaderKey() {
      return "fake";
    }

    @Override
    public List<DataSourceConnectionConfig> readConnectionConfigList(List<String> connectionList) {
      return connectionList.stream()
          .filter(connection -> connection.startsWith("db_"))
          .map(connection -> {
            var config = new DataSourceConnectionConfig();
            config.setConnection(connection);
            config.setUrl("jdbc:mariadb://localhost:1/" + connection);
            config.setUsername("user");
            config.setPassword("password");
            return config;
          })
          .toList();
    }
  }
}
