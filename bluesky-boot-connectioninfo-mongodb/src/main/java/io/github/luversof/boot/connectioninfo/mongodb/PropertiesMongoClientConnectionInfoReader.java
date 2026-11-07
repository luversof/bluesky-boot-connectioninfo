package io.github.luversof.boot.connectioninfo.mongodb;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.MongoClientConnectionConfig;
import io.github.luversof.boot.security.crypto.factory.TextEncryptorFactories;

/**
 * Properties 파일에서 MongoDB 연결 정보를 읽어오는 Reader
 * 
 * <p>
 * 기본 설정은 connection-info.mongodb.* 에서 읽으며, 프로젝트의 application.properties에서
 * override 가능합니다.
 * 
 * <p>
 * 기본 설정 예시:
 * 
 * <pre>
 * # 기본 연결 설정
 * bluesky-boot.connection-info.mongodb.host=localhost
 * bluesky-boot.connection-info.mongodb.port=27017
 * bluesky-boot.connection-info.mongodb.authentication-database=admin
 * 
 * # Connection Pool 설정
 * bluesky-boot.connection-info.mongodb.connection-pool.max-size=30
 * bluesky-boot.connection-info.mongodb.connection-pool.min-size=10
 * bluesky-boot.connection-info.mongodb.connection-pool.max-wait-time-ms=120000
 * 
 * # Read/Write 설정
 * bluesky-boot.connection-info.mongodb.read-concern-level=local
 * bluesky-boot.connection-info.mongodb.read-preference=secondaryPreferred
 * bluesky-boot.connection-info.mongodb.write-concern.w=W1
 * bluesky-boot.connection-info.mongodb.write-concern.w-timeout-ms=5000
 * bluesky-boot.connection-info.mongodb.write-concern.journal=false
 * 
 * # UUID Representation
 * bluesky-boot.connection-info.mongodb.uuid-representation=JAVA_LEGACY
 * </pre>
 * 
 * <p>
 * 개별 연결 정보:
 * 
 * <pre>
 * bluesky-boot.connection-info.mongodb.connection-map.{connectionName}.hosts=host1:port1,host2:port2
 * bluesky-boot.connection-info.mongodb.connection-map.{connectionName}.database=dbname
 * bluesky-boot.connection-info.mongodb.connection-map.{connectionName}.username={text}encryptedUser

 * bluesky-boot.connection-info.mongodb.connection-map.{connectionName}.password={text}encryptedPassword
 * </pre>
 */
public class PropertiesMongoClientConnectionInfoReader implements ConnectionInfoReader<MongoClientConnectionConfig> {

	protected String readerKey = "properties-mongoclient";

	protected final ConnectionInfoProperties connectionInfoProperties;
	protected final MongoDbDefaultProperties defaultProperties;
	protected final MongoDbConnectionMapProperties connectionMapProperties;

	public PropertiesMongoClientConnectionInfoReader(
			ConnectionInfoProperties connectionInfoProperties,
			MongoDbDefaultProperties defaultProperties,
			MongoDbConnectionMapProperties connectionMapProperties) {
		this.connectionInfoProperties = connectionInfoProperties;
		this.defaultProperties = defaultProperties;
		this.connectionMapProperties = connectionMapProperties;
	}

	@Override
	public String getReaderKey() {
		return readerKey;
	}

	@Override
	public List<MongoClientConnectionConfig> readConnectionConfigList(List<String> connectionList) {
		List<MongoClientConnectionConfig> configList = new ArrayList<>();
		var encryptor = TextEncryptorFactories.getDelegatingTextEncryptor();

		for (String connectionName : connectionList) {
			// MongoDbConnectionMapProperties에서 설정 가져오기
			MongoDbConnectionMapProperties.MongoConnectionConfig connConfig = connectionMapProperties.getConnectionMap()
					.get(connectionName);

			if (connConfig == null || connConfig.getHosts() == null || connConfig.getDatabase() == null) {
				// 필수 정보가 없으면 skip
				continue;
			}

			MongoClientConnectionConfig config = new MongoClientConnectionConfig();
			config.setConnection(connectionName);
			config.setDatabase(connConfig.getDatabase());

			// username/password는 암호화되어 있을 수 있으므로 복호화
			String username = connConfig.getUsername() == null ? null : encryptor.decrypt(connConfig.getUsername());
			String password = connConfig.getPassword() == null ? null : encryptor.decrypt(connConfig.getPassword());
			config.setUserName(username);
			config.setPassword(password);
			config.setConnectionString(buildConnectionString(connConfig, username, password));

			configList.add(config);
		}

		return configList;
	}

	/** 드라이버는 '+' 를 공백으로 풀지 않으므로 URLEncoder 의 '+' 를 %20 으로 바꾼다. */
	private static String encodeUserInfo(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/**
	 * MongoDB connectionString을 생성합니다.
	 *
	 * @param connConfig MongoDB 연결 설정
	 * @param username 복호화한 사용자명
	 * @param password 복호화한 비밀번호
	 * @return MongoDB connectionString
	 */
	String buildConnectionString(MongoDbConnectionMapProperties.MongoConnectionConfig connConfig, String username, String password) {
		StringBuilder connectionString = new StringBuilder("mongodb://");

		// username/password가 있으면 추가. '@' ':' '/' '%' 가 든 비밀번호도 되도록 URL 인코딩한다(드라이버가 디코딩한다)
		if (username != null && password != null) {
			connectionString.append(encodeUserInfo(username))
					.append(":")
					.append(encodeUserInfo(password))
					.append("@");
		}

		// hosts 추가 (이미 포트를 포함한 형식: "host:port" 또는 "host1:port1,host2:port2")
		connectionString.append(connConfig.getHosts());

		// database 추가
		connectionString.append("/").append(connConfig.getDatabase());

		// authSource 추가
		if (username != null) {
			connectionString.append("?authSource=").append(defaultProperties.getAuthenticationDatabase());
		}

		return connectionString.toString();
	}
}
