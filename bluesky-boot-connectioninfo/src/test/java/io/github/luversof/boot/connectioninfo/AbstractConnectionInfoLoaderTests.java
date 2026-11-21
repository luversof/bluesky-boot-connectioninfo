package io.github.luversof.boot.connectioninfo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties.ConnectionInfoLoaderProperties;

class AbstractConnectionInfoLoaderTests {

  @Test
  void keepsFirstReaderConfigWhenReadersReturnSameConnection() {
    // 같은 connection을 두 리더가 알면(대소문자만 다른 경우 포함) 하나만 만들어야 닫히지 않는 풀이 생기지 않는다
    var loader = new TestLoader(List.of(reader("first", "db1"), reader("second", "DB1")));

    var connectionInfoList = loader.load(List.of("db1"));

    assertThat(connectionInfoList)
        .extracting(connectionInfo -> ((TestConnection) connectionInfo.getConnection()).reader())
        .containsExactly("first");
  }

  @Test
  void skipsConfigWithoutConnectionName() {
    var loader = new TestLoader(List.of(reader("first", null, " ", "db1")));

    var connectionInfoList = loader.load(List.of("db1", "db2"));

    assertThat(connectionInfoList)
        .extracting(connectionInfo -> connectionInfo.getKey().connectionKey())
        .containsExactly("db1");
  }

  @Test
  void returnsEmptyWithoutConnectionsOrReaders() {
    assertThat(new TestLoader(List.of(reader("first", "db1"))).load(List.of())).isEmpty();
    assertThat(new TestLoader(List.of()).load(List.of("db1"))).isEmpty();
    assertThat(new TestLoader(List.of(reader("empty"))).load(List.of("db1"))).isEmpty();
  }

  @Test
  void loadReadsConfiguredConnectionsOnce() {
    var properties = new ConnectionInfoProperties();
    properties
        .getLoaders()
        .put(
            "test",
            new ConnectionInfoLoaderProperties(
                true, Map.of("a", List.of("db1", "db2"), "b", List.of("db1"))));
    var loader = new TestLoader(properties, List.of(reader("first", "db1", "db2")));

    assertThat(loader.load())
        .extracting(connectionInfo -> connectionInfo.getKey().connectionKey())
        .containsExactlyInAnyOrder("db1", "db2");
  }

  @Test
  void loadReturnsEmptyWhenLoaderIsNotConfigured() {
    var readers = List.of(reader("first", "db1"));
    assertThat(new TestLoader(null, readers).load()).isEmpty();

    var nullLoaders = new ConnectionInfoProperties();
    nullLoaders.setLoaders(null);
    assertThat(new TestLoader(nullLoaders, readers).load()).isEmpty();

    assertThat(new TestLoader(new ConnectionInfoProperties(), readers).load()).isEmpty();

    var noConnections = new ConnectionInfoProperties();
    noConnections.getLoaders().put("test", new ConnectionInfoLoaderProperties(true, Map.of()));
    assertThat(new TestLoader(noConnections, readers).load()).isEmpty();
  }

  @Test
  void destroyClosesCreatedConnections() {
    var loader = new TestLoader(List.of(reader("first", "db1", "db2")));
    loader.load(List.of("db1", "db2"));

    loader.destroy();

    assertThat(loader.closed).containsExactly("db1", "db2");
  }

  @Test
  void destroyKeepsClosingWhenOneCloseFailsAndSkipsNonCloseable() {
    // plain: AutoCloseable이 아닌 connection, fail: close()가 예외를 던지는 connection
    var loader = new TestLoader(List.of(reader("first", "plain", "fail", "db1")));
    loader.load(List.of("plain", "fail", "db1"));

    loader.destroy();

    assertThat(loader.closed).containsExactly("db1");
  }

  private static ConnectionInfoReader<TestConfig> reader(String readerKey, String... connections) {
    return new ConnectionInfoReader<>() {
      @Override
      public String getReaderKey() {
        return readerKey;
      }

      @Override
      public List<TestConfig> readConnectionConfigList(List<String> connectionList) {
        return Arrays.stream(connections)
            .map(connection -> connection == null ? null : new TestConfig(connection, readerKey))
            .toList();
      }
    };
  }

  record TestConfig(String getConnection, String reader) implements ConnectionConfig {}

  static class TestLoader extends AbstractConnectionInfoLoader<Object, TestConfig> {

    final List<String> closed = new ArrayList<>();

    TestLoader(List<ConnectionInfoReader<TestConfig>> readers) {
      this(new ConnectionInfoProperties(), readers);
    }

    TestLoader(
        ConnectionInfoProperties properties, List<ConnectionInfoReader<TestConfig>> readers) {
      super(properties, readers);
    }

    @Override
    public String getLoaderKey() {
      return "test";
    }

    @Override
    protected ConnectionInfo<Object> createConnectionInfo(TestConfig connectionConfig) {
      var name = connectionConfig.getConnection();
      Object connection =
          switch (name) {
            case "plain" -> name;
            case "fail" ->
                new TestConnection(
                    connectionConfig.reader(),
                    () -> {
                      throw new IllegalStateException("close failed");
                    });
            default -> new TestConnection(connectionConfig.reader(), () -> closed.add(name));
          };
      return new ConnectionInfo<>(new ConnectionInfoKey(getLoaderKey(), name), connection);
    }
  }

  record TestConnection(String reader, Runnable onClose) implements AutoCloseable {
    @Override
    public void close() {
      onClose.run();
    }
  }
}
