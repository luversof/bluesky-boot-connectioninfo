package io.github.luversof.boot.connectioninfo;

import java.util.Map;
import java.util.Objects;

public class DataSourceConnectionConfig implements ConnectionConfig {

  private String connection;

  private String url;

  private String username;

  private String password;

  private Map<String, Object> extradata;

  public String getConnection() {
    return connection;
  }

  public void setConnection(String connection) {
    this.connection = connection;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Map<String, Object> getExtradata() {
    return extradata;
  }

  public void setExtradata(Map<String, Object> extradata) {
    this.extradata = extradata;
  }

  @Override
  public String toString() {
    return "DataSourceConnectionConfig(connection="
        + this.getConnection()
        + ", url="
        + this.getUrl()
        + ", username="
        + this.getUsername()
        + ", password="
        + (this.getPassword() == null ? null : "****")
        + ", extradata="
        + this.getExtradata()
        + ")";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof DataSourceConnectionConfig other)) return false;
    return other.canEqual(this)
        && Objects.equals(this.getConnection(), other.getConnection())
        && Objects.equals(this.getUrl(), other.getUrl())
        && Objects.equals(this.getUsername(), other.getUsername())
        && Objects.equals(this.getPassword(), other.getPassword())
        && Objects.equals(this.getExtradata(), other.getExtradata());
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DataSourceConnectionConfig;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        this.getConnection(),
        this.getUrl(),
        this.getUsername(),
        this.getPassword(),
        this.getExtradata());
  }
}
