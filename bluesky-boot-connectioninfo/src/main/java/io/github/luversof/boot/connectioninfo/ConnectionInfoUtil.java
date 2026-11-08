package io.github.luversof.boot.connectioninfo;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;

public final class ConnectionInfoUtil {

  private static volatile ApplicationContext applicationContext;

  /**
   * LazyLoad를 connectionKey 단위로 직렬화하기 위한 lock. 개수가 고정이라 요청 key가 늘어도 메모리가 늘지 않고, registry처럼 대소문자를
   * 구분하지 않는다. 다른 key가 같은 lock을 나눠 쓸 수 있지만 LazyLoad는 드물어 문제되지 않는다.
   */
  private static final Object[] lazyLoadLocks = new Object[64];

  static {
    Arrays.setAll(lazyLoadLocks, _ -> new Object());
  }

  /** 못 찾은 connection(타입:key)과 다시 조회할 수 있는 시각. 없는 key를 부를 때마다 메타 DB를 다시 조회하지 않기 위함이다. */
  private static final Map<String, Long> missUntilMap = new ConcurrentHashMap<>();

  private static final long MISS_CACHE_MILLIS = 60_000L;

  private static final int MISS_CACHE_MAX_SIZE = 10_000;

  private ConnectionInfoUtil() {}

  public static void setApplicationContext(ApplicationContext applicationContext) {
    ConnectionInfoUtil.applicationContext = applicationContext;
    missUntilMap.clear();
  }

  private static Object lazyLoadLock(String connectionKey) {
    return lazyLoadLocks[
        Math.floorMod(connectionKey.toLowerCase(Locale.ROOT).hashCode(), lazyLoadLocks.length)];
  }

  private static String missKey(Class<?> connectionType, String connectionKey) {
    return connectionType.getName() + ":" + connectionKey.toLowerCase(Locale.ROOT);
  }

  private static boolean recentlyMissed(String missKey) {
    var missUntil = missUntilMap.get(missKey);
    return missUntil != null && missUntil > System.currentTimeMillis();
  }

  private static void recordMiss(String missKey) {
    // shortcut: 꽉 차면 통째로 비운다. 서로 다른 없는 key가 1분에 만 개를 넘게 들어오면 LRU로 바꾼다.
    if (missUntilMap.size() >= MISS_CACHE_MAX_SIZE) {
      missUntilMap.clear();
    }
    missUntilMap.put(missKey, System.currentTimeMillis() + MISS_CACHE_MILLIS);
  }

  /**
   * connectionKey에 해당하는 connection을 connectionType의 Registry/Loader에서만 찾는다.
   *
   * <p>connectionType은 Registry에 등록된 타입 그대로 지정한다(예: {@code HikariDataSource.class}, {@code
   * MongoClient.class}). LazyLoad 결과도 그 타입의 Registry에 추가되므로 다른 타입의 Registry가 섞이지 않는다.
   *
   * @param connectionKey connection key
   * @param connectionType Registry에 등록된 connection 타입
   * @return connection, 없으면 null
   */
  public static <T> T getConnection(String connectionKey, Class<T> connectionType) {
    ObjectProvider<ConnectionInfoRegistry<T>> connectionInfoRegistryProvider =
        applicationContext.getBeanProvider(
            ResolvableType.forClassWithGenerics(ConnectionInfoRegistry.class, connectionType));
    var targetRegistry = connectionInfoRegistryProvider.orderedStream().findFirst().orElse(null);
    if (targetRegistry == null) {
      return null;
    }

    var connection = findConnection(connectionInfoRegistryProvider, connectionKey);
    if (connection != null) {
      return connection;
    }

    var missKey = missKey(connectionType, connectionKey);
    if (recentlyMissed(missKey)) {
      return null;
    }

    synchronized (lazyLoadLock(connectionKey)) {
      connection = findConnection(connectionInfoRegistryProvider, connectionKey);
      if (connection != null) {
        return connection;
      }
      if (recentlyMissed(missKey)) {
        return null;
      }

      // Loader는 두 번째 제네릭(ConnectionConfig)이 제각각이라 타입으로 찾을 수 없어 클래스의 제네릭으로 거른다.
      for (var loader : applicationContext.getBeanProvider(ConnectionInfoLoader.class)) {
        if (!loadsType(loader, connectionType)) {
          continue;
        }
        @SuppressWarnings("unchecked")
        List<ConnectionInfo<T>> connectionInfoList = loader.load(List.of(connectionKey));
        if (connectionInfoList != null && !connectionInfoList.isEmpty()) {
          targetRegistry.addConnectionInfoList(connectionInfoList);
          return connectionInfoList.getFirst().getConnection();
        }
      }
      recordMiss(missKey);
    }
    return null;
  }

  private static boolean loadsType(Object loader, Class<?> connectionType) {
    var loadedType =
        ResolvableType.forClass(ClassUtils.getUserClass(loader))
            .as(ConnectionInfoLoader.class)
            .resolveGeneric(0);
    return loadedType != null && connectionType.isAssignableFrom(loadedType);
  }

  /**
   * connectionKey에 해당하는 connection을 모든 Registry/Loader에서 찾는다.
   *
   * <p>반환 타입을 검사하지 않고, LazyLoad 결과가 다른 타입의 Registry에 추가될 수 있다. JDBC와 MongoDB를 함께 쓰면 {@link
   * #getConnection(String, Class)}를 사용한다.
   */
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
    // (lock은 고정 개수라 다른 connectionKey가 가끔 같은 lock을 기다릴 수 있다)
    // 못 찾은 key는 MISS_CACHE_MILLIS 동안 기억해 메타 DB를 다시 조회하지 않는다.
    var missKey = missKey(Object.class, connectionKey);
    if (recentlyMissed(missKey)) {
      return null;
    }
    synchronized (lazyLoadLock(connectionKey)) {
      // lock 대기 중 다른 스레드가 이미 LazyLoad를 끝냈을 수 있으므로 다시 확인한다.
      var connection = findConnection(connectionInfoRegistryProvider, connectionKey);
      if (connection != null) {
        return connection;
      }
      if (recentlyMissed(missKey)) {
        return null;
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
      recordMiss(missKey);
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
