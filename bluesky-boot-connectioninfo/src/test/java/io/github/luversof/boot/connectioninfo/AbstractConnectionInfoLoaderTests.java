package io.github.luversof.boot.connectioninfo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class AbstractConnectionInfoLoaderTests {

  @Test
  void keepsFirstReaderConfigWhenReadersReturnSameConnection() {
    // 같은 connection을 두 리더가 알면(대소문자만 다른 경우 포함) 하나만 만들어야 닫히지 않는 풀이 생기지 않는다
    var loader = new TestLoader(List.of(reader("first", "db1"), reader("second", "DB1")));

    var connectionInfoList = loader.load(List.of("db1"));

    assertThat(connectionInfoList)
        .extracting(connectionInfo -> connectionInfo.getConnection().reader())
        .containsExactly("first");
  }

  @Test
  void destroyClosesCreatedConnections() {
    var loader = new TestLoader(List.of(reader("first", "db1", "db2")));
    loader.load(List.of("db1", "db2"));

    loader.destroy();

    assertThat(loader.closed).containsExactly("db1", "db2");
  }

  private static ConnectionInfoReader<TestConfig> reader(String readerKey, String... connections) {
    return new ConnectionInfoReader<>() {
      @Override
      public String getReaderKey() {
        return readerKey;
      }

      @Override
      public List<TestConfig> readConnectionConfigList(List<String> connectionList) {
        return List.of(connections).stream()
            .map(connection -> new TestConfig(connection, readerKey))
            .toList();
      }
    };
  }

  record TestConfig(String getConnection, String reader) implements ConnectionConfig {}

  static class TestLoader extends AbstractConnectionInfoLoader<TestConnection, TestConfig> {

    final List<String> closed = new ArrayList<>();

    TestLoader(List<ConnectionInfoReader<TestConfig>> readers) {
      super(new ConnectionInfoProperties(), readers);
    }

    @Override
    public String getLoaderKey() {
      return "test";
    }

    @Override
    protected ConnectionInfo<TestConnection> createConnectionInfo(TestConfig connectionConfig) {
      return new ConnectionInfo<>(
          new ConnectionInfoKey(getLoaderKey(), connectionConfig.getConnection()),
          new TestConnection(
              connectionConfig.reader(), () -> closed.add(connectionConfig.getConnection())));
    }
  }

  record TestConnection(String reader, Runnable onClose) implements AutoCloseable {
    @Override
    public void close() {
      onClose.run();
    }
  }
}
