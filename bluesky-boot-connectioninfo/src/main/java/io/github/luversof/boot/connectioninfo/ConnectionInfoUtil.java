package io.github.luversof.boot.connectioninfo;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;

public final class ConnectionInfoUtil {

  private static volatile ApplicationContext applicationContext;

  /** LazyLoad를 connectionKey 단위로 직렬화하기 위한 lock. connectionKey 개수만큼만 늘어난다. */
  private static final Map<String, Object> lazyLoadLockMap = new ConcurrentHashMap<>();

  private ConnectionInfoUtil() {}

  public static void setApplicationContext(ApplicationContext applicationContext) {
    ConnectionInfoUtil.applicationContext = applicationContext;
  }

  public static <T> T getConnection(String connectionKey) {
    // ConnectionInfoRegistry에서 기본 ConnectionInfo를 가져오고

    ObjectProvider<ConnectionInfoRegistry<T>> connectionInfoRegistryProvider =
        applicationContext.getBeanProvider(
            ResolvableType.forType(new ParameterizedTypeReference<ConnectionInfoRegistry<T>>() {}));
    // connectionKey에 해당하는 ConnectionInfo가 있으면 반환
    // 이미 로드된 Connection은 여기서 반환되므로 lock을 거치지 않는다.
    ConnectionInfoRegistry<T> targetRegistry = null;
    for (var registry : connectionInfoRegistryProvider) {
      targetRegistry = registry;
      var connectionInfoList = registry.getConnectionInfo(connectionKey);
      if (connectionInfoList != null && !connectionInfoList.isEmpty()) {
        return connectionInfoList.getFirst().getConnection();
      }
    }

    // Registry가 없으면 null 반환
    if (targetRegistry == null) {
      return null;
    }

    // 없으면 Loader를 통해 LazyLoad를 시도
    // 같은 connectionKey를 동시에 요청하면 Connection(MongoClient/DataSource)이 중복 생성되고,
    // registry에서 조회되지 않는 쪽은 닫히지 않은 채 프로세스 수명 내내 남는다. connectionKey 단위로 직렬화한다.
    // (다른 connectionKey끼리는 서로 다른 lock이므로 경합하지 않는다)
    synchronized (lazyLoadLockMap.computeIfAbsent(connectionKey, _ -> new Object())) {
      // lock 대기 중 다른 스레드가 이미 LazyLoad를 끝냈을 수 있으므로 다시 확인한다.
      var connection = findConnection(connectionInfoRegistryProvider, connectionKey);
      if (connection != null) {
        return connection;
      }

      ObjectProvider<ConnectionInfoLoader<T, ?>> connectionInfoLoaderProvider =
          applicationContext.getBeanProvider(
              ResolvableType.forType(
                  new ParameterizedTypeReference<ConnectionInfoLoader<T, ?>>() {}));
      for (var loader : connectionInfoLoaderProvider) {
        var connectionInfoList = loader.load(List.of(connectionKey));
        if (connectionInfoList != null && !connectionInfoList.isEmpty()) {
          targetRegistry.addConnectionInfoList(connectionInfoList);
          return connectionInfoList.getFirst().getConnection();
        }
      }
    }

    // 그래도 없으면 null 반환
    return null;
  }

  private static <T> T findConnection(
      ObjectProvider<ConnectionInfoRegistry<T>> connectionInfoRegistryProvider,
      String connectionKey) {
    for (var registry : connectionInfoRegistryProvider) {
      var connectionInfoList = registry.getConnectionInfo(connectionKey);
      if (connectionInfoList != null && !connectionInfoList.isEmpty()) {
        return connectionInfoList.getFirst().getConnection();
      }
    }

    // 그래도 없으면 null 반환
    return null;
  }

  /**
   * connectionKey에 해당하는 connectionName을 반환하는 유틸리티 메서드 connectionKey -> connectionName ->
   * ConnectionInfo -> Connection
   */
  public static String getConnectionName(String connectionKey) {
    var connectionInfoProperties = applicationContext.getBean(ConnectionInfoProperties.class);

    if (connectionInfoProperties == null || connectionInfoProperties.getLoaders() == null) {
      throw new IllegalStateException("connection-info.loaders is not configured");
    }

    return connectionInfoProperties.getLoaders().values().stream()
        .map(ConnectionInfoProperties.ConnectionInfoLoaderProperties::getConnections)
        .filter(Objects::nonNull)
        .map(connections -> connections.get(connectionKey))
        .filter(connectionList -> connectionList != null && !connectionList.isEmpty())
        .map(List::getFirst)
        .findFirst()
        .orElseThrow(() ->
            new IllegalStateException(
                "connection-info connection is not configured: " + connectionKey));
  }

  /**
   * connectionKey에 해당하는 connectionName List를 반환하는 유틸리티 메서드
   *
   * @param connectionKey
   * @return
   */
  public static List<String> getConnectionNameList(String connectionKey) {
    var connectionInfoProperties = applicationContext.getBean(ConnectionInfoProperties.class);

    if (connectionInfoProperties == null || connectionInfoProperties.getLoaders() == null) {
      throw new IllegalStateException("connection-info.loaders is not configured");
    }

    return connectionInfoProperties.getLoaders().values().stream()
        .map(ConnectionInfoProperties.ConnectionInfoLoaderProperties::getConnections)
        .filter(Objects::nonNull)
        .map(connections -> connections.get(connectionKey))
        .filter(connectionList -> connectionList != null && !connectionList.isEmpty())
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }
}
