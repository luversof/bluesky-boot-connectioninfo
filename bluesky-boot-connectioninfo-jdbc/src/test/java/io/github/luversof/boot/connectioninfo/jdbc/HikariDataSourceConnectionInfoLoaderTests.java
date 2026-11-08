package io.github.luversof.boot.connectioninfo.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties.ConnectionInfoLoaderProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.DataSourceConnectionConfig;

class HikariDataSourceConnectionInfoLoaderTests {

  @Test
  void appliesLoaderPropertiesToCreatedDataSource() {
    var loaderProperties = new ConnectionInfoLoaderProperties();
    // initializationFailTimeout=-1: DB 없이도 풀을 만들 수 있게 첫 연결 시도를 건너뛴다
    loaderProperties.setProperties(
        Map.of("maximumPoolSize", "5", "minimumIdle", "0", "initializationFailTimeout", "-1"));
    var loader = loader(loaderProperties);

    var connectionInfoList = loader.load(List.of("db1"));

    try {
      assertThat(connectionInfoList).hasSize(1);
      var dataSource = connectionInfoList.getFirst().getConnection();
      assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
      assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:mariadb://localhost:1/db1");
    } finally {
      loader.destroy();
    }
  }

  @Test
  void usesHikariDefaultsWithoutLoaderProperties() {
    var loader = loader(new ConnectionInfoLoaderProperties());

    assertThat(loader.createHikariConfig().getMaximumPoolSize())
        .isEqualTo(new com.zaxxer.hikari.HikariConfig().getMaximumPoolSize());
  }

  @Test
  void emptyConnectionListSkipsReaders() {
    // 빈 목록으로 리더를 부르면 "IN ()" SQL 오류가 나므로 리더까지 가면 안 된다
    ConnectionInfoReader<DataSourceConnectionConfig> failingReader =
        new ConnectionInfoReader<>() {
          @Override
          public String getReaderKey() {
            return "failing";
          }

          @Override
          public List<DataSourceConnectionConfig> readConnectionConfigList(
              List<String> connectionList) {
            throw new AssertionError("reader must not be called");
          }
        };
    var loader =
        new HikariDataSourceConnectionInfoLoader(
            new ConnectionInfoProperties(), List.of(failingReader));

    assertThat(loader.load(List.of())).isEmpty();
  }

  private static HikariDataSourceConnectionInfoLoader loader(
      ConnectionInfoLoaderProperties loaderProperties) {
    var connectionInfoProperties = new ConnectionInfoProperties();
    connectionInfoProperties.getLoaders().put("hikaridatasource", loaderProperties);
    ConnectionInfoReader<DataSourceConnectionConfig> reader =
        new ConnectionInfoReader<>() {
          @Override
          public String getReaderKey() {
            return "test";
          }

          @Override
          public List<DataSourceConnectionConfig> readConnectionConfigList(
              List<String> connectionList) {
            return connectionList.stream()
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
        };
    return new HikariDataSourceConnectionInfoLoader(connectionInfoProperties, List.of(reader));
  }
}
