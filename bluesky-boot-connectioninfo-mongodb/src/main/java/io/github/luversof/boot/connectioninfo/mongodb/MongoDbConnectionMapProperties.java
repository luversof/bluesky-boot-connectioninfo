package io.github.luversof.boot.connectioninfo.mongodb;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MongoDB connection-map properties 바인딩
 *
 * <p>connection-info.mongodb.connection-map.{connectionName}.*
 */
@ConfigurationProperties(prefix = "bluesky-boot.connection-info.mongodb")
public class MongoDbConnectionMapProperties {

  /** Key: connectionName Value: MongoConnectionConfig */
  private Map<String, MongoConnectionConfig> connectionMap = new HashMap<>();

  public Map<String, MongoConnectionConfig> getConnectionMap() {
    return connectionMap;
  }

  public void setConnectionMap(Map<String, MongoConnectionConfig> connectionMap) {
    this.connectionMap = connectionMap;
  }

  /** 개별 MongoDB 연결 설정 (필수 정보만) */
  public static class MongoConnectionConfig {

    /** 호스트:포트 목록 단일 서버: "localhost:27017" Replica Set: "host1:27017,host2:27017,host3:27017" */
    private String hosts;

    /** 데이터베이스 이름 */
    private String database;

    /** 사용자명 (암호화 가능: {text}encrypted...) */
    private String username;

    /** 비밀번호 (암호화 가능: {text}encrypted...) */
    private String password;

    public String getHosts() {
      return hosts;
    }

    public void setHosts(String hosts) {
      this.hosts = hosts;
    }

    public String getDatabase() {
      return database;
    }

    public void setDatabase(String database) {
      this.database = database;
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
  }
}
