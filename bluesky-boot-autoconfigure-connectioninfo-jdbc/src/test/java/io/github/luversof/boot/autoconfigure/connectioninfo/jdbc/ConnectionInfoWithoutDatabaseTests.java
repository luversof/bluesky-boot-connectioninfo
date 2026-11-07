package io.github.luversof.boot.autoconfigure.connectioninfo.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import io.github.luversof.boot.autoconfigure.connectioninfo.ConnectionInfoAutoConfiguration;
import io.github.luversof.boot.connectioninfo.ConnectionInfo;
import io.github.luversof.boot.connectioninfo.ConnectionInfoDevCheckController;
import io.github.luversof.boot.connectioninfo.ConnectionInfoKey;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.DataSourceConnectionConfig;
import io.github.luversof.boot.connectioninfo.jdbc.AbstractDataSourceConnectionInfoLoader;

/** 실제 DB 없이 도는 시험. */
class ConnectionInfoWithoutDatabaseTests {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConnectionInfoAutoConfiguration.class,
                  ConnectionInfoJdbcAutoConfiguration.class));

  @Test
  void devCheckControllerOnlyWhenDevCheckEnabled() {
    this.contextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context).doesNotHaveBean(ConnectionInfoDevCheckController.class);
    });
    this.contextRunner
        .withPropertyValues("bluesky-boot.dev-check.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(ConnectionInfoDevCheckController.class));
  }

  @Test
  void loaderClosesCreatedDataSourcesOnDestroy() {
    var loader = new ClosableLoader();
    var created = loader.load(List.of("a", "b"));
    assertThat(created).hasSize(2);

    loader.destroy();

    assertThat(loader.closed).containsExactly("a", "b");
  }

  @Test
  void contextCloseClosesLoaderPools() {
    var loader = new ClosableLoader();
    this.contextRunner
        .withBean(ClosableLoader.class, () -> loader)
        .run(context -> loader.load(List.of("a")));

    assertThat(loader.closed).containsExactly("a");
  }

  @Test
  void toStringHidesPassword() {
    var config = new DataSourceConnectionConfig();
    config.setPassword("secret");

    assertThat(config.toString()).doesNotContain("secret").contains("password=****");
  }

  /** DB 에 붙지 않는 DataSource 를 만들어 close 여부만 센다. */
  static class ClosableLoader
      extends AbstractDataSourceConnectionInfoLoader<
          ClosableDataSource, DataSourceConnectionConfig> {

    final List<String> closed = new ArrayList<>();

    ClosableLoader() {
      super(
          new ConnectionInfoProperties(),
          List.of(
              new ConnectionInfoReader<>() {
                @Override
                public String getReaderKey() {
                  return "fake";
                }

                @Override
                public List<DataSourceConnectionConfig> readConnectionConfigList(
                    List<String> connectionList) {
                  return connectionList.stream()
                      .map(connection -> {
                        var config = new DataSourceConnectionConfig();
                        config.setConnection(connection);
                        return config;
                      })
                      .toList();
                }
              }));
    }

    @Override
    public String getLoaderKey() {
      return "fake";
    }

    @Override
    protected ConnectionInfo<ClosableDataSource> createConnectionInfo(
        DataSourceConnectionConfig connectionConfig) {
      var dataSource = new ClosableDataSource(() -> closed.add(connectionConfig.getConnection()));
      createdDataSources.add(dataSource);
      return new ConnectionInfo<>(
          new ConnectionInfoKey(getLoaderKey(), connectionConfig.getConnection()), dataSource);
    }
  }

  static class ClosableDataSource extends SimpleDriverDataSource implements AutoCloseable {

    private final Runnable onClose;

    ClosableDataSource(Runnable onClose) {
      this.onClose = onClose;
    }

    @Override
    public void close() {
      onClose.run();
    }
  }
}
