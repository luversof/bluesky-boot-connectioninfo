package io.github.luversof.boot.connectioninfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Reader들로 설정을 읽어 connection을 만드는 Loader의 공통 동작.
 *
 * <p>하위 클래스는 설정 하나로 connection을 만드는 {@link #createConnectionInfo}만 구현한다. 만든 connection 중 {@link
 * AutoCloseable}인 것은 앱 종료 시 닫는다.
 *
 * @param <T> 만들 connection 타입
 * @param <C> Reader가 읽는 설정 타입
 */
public abstract class AbstractConnectionInfoLoader<T, C extends ConnectionConfig>
    implements ConnectionInfoLoader<T, C>, DisposableBean {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected final ConnectionInfoProperties connectionInfoProperties;

  protected final List<ConnectionInfoReader<C>> connectionInfoReaderList;

  // 이 로더가 만든 connection. 앱 종료 시 close()로 커넥션 풀/서버 세션을 반납하기 위해 추적한다.
  private final List<T> createdConnections = new CopyOnWriteArrayList<>();

  protected AbstractConnectionInfoLoader(
      ConnectionInfoProperties connectionInfoProperties,
      List<ConnectionInfoReader<C>> connectionInfoReaderList) {
    this.connectionInfoProperties = connectionInfoProperties;
    this.connectionInfoReaderList = connectionInfoReaderList;
  }

  @Override
  public List<ConnectionInfoReader<C>> getConnectionInfoReaderList() {
    return connectionInfoReaderList;
  }

  @Override
  public List<ConnectionInfo<T>> load() {
    if (connectionInfoProperties == null
        || connectionInfoProperties.getLoaders() == null
        || !connectionInfoProperties.getLoaders().containsKey(getLoaderKey())
        || CollectionUtils.isEmpty(
            connectionInfoProperties.getLoaders().get(getLoaderKey()).getConnections())) {
      return Collections.emptyList();
    }

    List<String> connectionList =
        connectionInfoProperties.getLoaders().get(getLoaderKey()).getConnections().values().stream()
            .flatMap(List::stream)
            .distinct()
            .toList();

    return load(connectionList);
  }

  @Override
  public List<ConnectionInfo<T>> load(List<String> connectionList) {
    // 빈 목록으로 조회하면 JDBC 리더 쿼리가 "IN ()"이 되어 SQL 문법 오류가 난다.
    if (CollectionUtils.isEmpty(connectionList)
        || CollectionUtils.isEmpty(getConnectionInfoReaderList())) {
      return Collections.emptyList();
    }

    log.debug(
        "connectionInfoReaderKeyList : {}",
        getConnectionInfoReaderList().stream().map(ConnectionInfoReader::getReaderKey).toList());

    var connectionConfigList = new ArrayList<C>();
    for (var connectionInfoReader : getConnectionInfoReaderList()) {
      var readConnectionConfigList = connectionInfoReader.readConnectionConfigList(connectionList);
      if (!CollectionUtils.isEmpty(readConnectionConfigList)) {
        addConnectionConfigList(
            connectionConfigList, readConnectionConfigList, connectionInfoReader);
      }
    }

    connectionList.forEach(connection -> {
      if (containsConnection(connectionConfigList, connection)) {
        log.debug("find database connection ({})", connection);
      } else {
        log.debug("cannot find database connection ({})", connection);
      }
    });

    var connectionInfoList = new ArrayList<ConnectionInfo<T>>();
    for (var connectionConfig : connectionConfigList) {
      var connectionInfo = createConnectionInfo(connectionConfig);
      createdConnections.add(connectionInfo.getConnection());
      connectionInfoList.add(connectionInfo);
    }
    return connectionInfoList;
  }

  /**
   * 리더가 조회한 설정을 connection 이름 기준으로 중복 없이 누적한다.
   *
   * <p>여러 리더가 같은 connection을 알고 있으면(예: mysql/mariadb 리더, mongodb/properties 리더를 함께 사용) 같은
   * connection이 중복 생성되고, registry에서 조회되지 않는 쪽은 닫히지 않은 채 남는다. 먼저 조회된 리더의 설정을 유지한다.
   */
  private void addConnectionConfigList(
      List<C> connectionConfigList,
      List<C> readConnectionConfigList,
      ConnectionInfoReader<C> connectionInfoReader) {
    for (var connectionConfig : readConnectionConfigList) {
      if (connectionConfig == null || !StringUtils.hasText(connectionConfig.getConnection())) {
        log.warn(
            "skip database connection without connection name from reader ({})",
            connectionInfoReader.getReaderKey());
        continue;
      }

      if (containsConnection(connectionConfigList, connectionConfig.getConnection())) {
        log.warn(
            "skip duplicated database connection ({}) from reader ({})",
            connectionConfig.getConnection(),
            connectionInfoReader.getReaderKey());
        continue;
      }

      connectionConfigList.add(connectionConfig);
    }
  }

  private boolean containsConnection(List<C> connectionConfigList, String connection) {
    return connectionConfigList.stream()
        .anyMatch(
            connectionConfig -> connectionConfig.getConnection().equalsIgnoreCase(connection));
  }

  /**
   * 설정 하나로 connection을 만든다.
   *
   * @param connectionConfig Reader가 읽은 설정
   * @return 만든 connection
   */
  protected abstract ConnectionInfo<T> createConnectionInfo(C connectionConfig);

  /** 앱 종료 시 이 로더가 만든 connection을 닫는다. 닫지 않으면 재시작·컨텍스트 재생성 때마다 풀과 서버 세션이 남는다. */
  @Override
  public void destroy() {
    for (var connection : createdConnections) {
      if (connection instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception exception) {
          log.warn("Failed to close connection on shutdown", exception);
        }
      }
    }
    createdConnections.clear();
  }
}
