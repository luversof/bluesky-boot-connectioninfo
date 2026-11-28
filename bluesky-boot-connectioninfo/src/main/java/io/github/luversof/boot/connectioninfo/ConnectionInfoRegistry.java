package io.github.luversof.boot.connectioninfo;

import java.util.List;

/**
 * connectionInfo 객체를 담고 있는 Registry
 *
 * @author bluesky
 * @param <T> connection 객체 타입
 */
@FunctionalInterface
public interface ConnectionInfoRegistry<T> {

  /**
   * connectionInfo 객체를 담고 있는 List
   *
   * @return
   */
  List<ConnectionInfo<T>> getConnectionInfoList();

  /**
   * connectionInfo 객체를 List에 추가
   *
   * @param connectionInfo
   */
  default void addConnectionInfo(ConnectionInfo<T> connectionInfo) {
    getConnectionInfoList().add(connectionInfo);
  }

  /**
   * connectionInfo 객체 List를 List에 추가
   *
   * @param connectionInfoList
   */
  default void addConnectionInfoList(List<ConnectionInfo<T>> connectionInfoList) {
    getConnectionInfoList().addAll(connectionInfoList);
  }

  /**
   * connectionInfoKey에 해당하는 connectionInfo 객체를 반환
   *
   * @param connectionInfoKey
   * @return
   */
  default ConnectionInfo<T> getConnectionInfo(ConnectionInfoKey connectionInfoKey) {
    // connectionKey는 문자열 조회와 같은 규칙(대소문자 무시)으로 비교한다
    return getConnectionInfoList().stream()
        .filter(connectionInfo ->
            connectionInfo.getKey().loaderKey().equals(connectionInfoKey.loaderKey())
                && connectionInfo
                    .getKey()
                    .connectionKey()
                    .equalsIgnoreCase(connectionInfoKey.connectionKey()))
        .findAny()
        .orElseThrow(() ->
            new IllegalStateException("connection info does not exist: " + connectionInfoKey));
  }

  /**
   * connectionKey에 해당하는 connectionInfo 객체 List를 반환
   *
   * @param connectionKey
   * @return
   */
  default List<ConnectionInfo<T>> getConnectionInfo(String connectionKey) {
    // 로더가 connection 이름을 대소문자 구분 없이 다루고 key는 저장소의 이름으로 만들므로 같은 규칙으로 찾는다.
    // 다르게 비교하면 매번 못 찾아 LazyLoad가 connection을 계속 새로 만든다.
    return getConnectionInfoList().stream()
        .filter(connectionInfo ->
            connectionInfo.getKey().connectionKey().equalsIgnoreCase(connectionKey))
        .toList();
  }
}
