package io.github.luversof.boot.connectioninfo;

import java.util.Objects;

/**
 * connection info가 최종적으로 생성하려고 하는 대상 객체
 *
 * @param <T>
 */
public class ConnectionInfo<T> {

  /** connection info key */
  private final ConnectionInfoKey key;

  /** connection 객체 */
  private final T connection;

  public ConnectionInfo(ConnectionInfoKey key, T connection) {
    this.key = key;
    this.connection = connection;
  }

  public ConnectionInfoKey getKey() {
    return key;
  }

  public T getConnection() {
    return connection;
  }

  @Override
  public String toString() {
    return "ConnectionInfo(key=" + this.getKey() + ", connection=" + this.getConnection() + ")";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof ConnectionInfo<?> other)) return false;
    return other.canEqual(this)
        && Objects.equals(this.getKey(), other.getKey())
        && Objects.equals(this.getConnection(), other.getConnection());
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ConnectionInfo;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.getKey(), this.getConnection());
  }
}
