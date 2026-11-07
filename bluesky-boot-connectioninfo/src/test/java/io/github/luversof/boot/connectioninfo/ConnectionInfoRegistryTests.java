package io.github.luversof.boot.connectioninfo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConnectionInfoRegistryTests {

  @Test
  void findsConnectionKeyIgnoringCase() {
    // 저장소에 'DB-Primary'로 저장되어 있어도 'db-primary'로 찾을 수 있어야 LazyLoad가 반복되지 않는다
    var connectionInfo =
        new ConnectionInfo<>(new ConnectionInfoKey("loader", "DB-Primary"), "connection");
    ConnectionInfoRegistry<String> registry = () -> List.of(connectionInfo);

    assertThat(registry.getConnectionInfo("db-primary")).containsExactly(connectionInfo);
    assertThat(registry.getConnectionInfo("db-secondary")).isEmpty();
  }
}
