package io.github.luversof.boot.connectioninfo.mongodb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.UuidRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadConcern;
import com.mongodb.ReadConcernLevel;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.connection.ConnectionPoolSettings;

import io.github.luversof.boot.connectioninfo.ConnectionInfo;
import io.github.luversof.boot.connectioninfo.ConnectionInfoKey;
import io.github.luversof.boot.connectioninfo.ConnectionInfoLoader;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.MongoClientConnectionConfig;

public class MongoDbMongoClientConnectionInfoLoader
		implements ConnectionInfoLoader<MongoClient, MongoClientConnectionConfig>, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(MongoDbMongoClientConnectionInfoLoader.class);

	protected String loaderKey = "mongoclient";

	protected final ConnectionInfoProperties connectionInfoProperties;

	protected final List<ConnectionInfoReader<MongoClientConnectionConfig>> connectionInfoReaderList;
	
	// connection-info.mongodb.* 기본 설정(pool/read/write 등). MongoClient 생성 시 적용한다.
	protected final MongoDbDefaultProperties defaultProperties;

	// 이 로더가 생성한 MongoClient들. 앱 종료 시 close()로 서버측 logical session을 반납(endSessions)하기 위해 추적한다.
	private final List<MongoClient> createdMongoClients = new CopyOnWriteArrayList<>();

	public MongoDbMongoClientConnectionInfoLoader(ConnectionInfoProperties connectionInfoProperties, List<ConnectionInfoReader<MongoClientConnectionConfig>> connectionInfoReaderList) {
		this(connectionInfoProperties, connectionInfoReaderList, new MongoDbDefaultProperties());
	}

	public MongoDbMongoClientConnectionInfoLoader(ConnectionInfoProperties connectionInfoProperties, List<ConnectionInfoReader<MongoClientConnectionConfig>> connectionInfoReaderList, MongoDbDefaultProperties defaultProperties) {
		this.connectionInfoProperties = connectionInfoProperties;
		this.connectionInfoReaderList = connectionInfoReaderList;
		this.defaultProperties = (defaultProperties != null) ? defaultProperties : new MongoDbDefaultProperties();
	}

	@Override
	public String getLoaderKey() {
		return loaderKey;
	}

	public List<ConnectionInfoReader<MongoClientConnectionConfig>> getConnectionInfoReaderList() {
		return connectionInfoReaderList;
	}

	@Override
	public List<ConnectionInfo<MongoClient>> load() {
		if (connectionInfoProperties == null
				|| connectionInfoProperties.getLoaders() == null
				|| !connectionInfoProperties.getLoaders().containsKey(getLoaderKey())
				|| connectionInfoProperties.getLoaders().get(getLoaderKey()).getConnections() == null) {
			return Collections.emptyList();
		}

		List<String> connectionList = connectionInfoProperties.getLoaders().get(getLoaderKey()).getConnections()
				.values().stream().flatMap(List::stream).distinct().toList();

		return load(connectionList);
	}

	@Override
	public List<ConnectionInfo<MongoClient>> load(List<String> connectionList) {
		if (connectionList == null || connectionList.isEmpty()) {
			return Collections.emptyList();
		}

		if (connectionInfoReaderList == null || connectionInfoReaderList.isEmpty()) {
			return Collections.emptyList();
		}

		var connectionConfigList = new ArrayList<MongoClientConnectionConfig>();
		getConnectionInfoReaderList().forEach(connectionInfoReader -> {
			var readConnectionConfigList = connectionInfoReader.readConnectionConfigList(connectionList);
			if (!CollectionUtils.isEmpty(readConnectionConfigList)) {
				addConnectionConfigList(connectionConfigList, readConnectionConfigList, connectionInfoReader);
			}
		});

		connectionList.forEach(connection -> {
			if (containsConnection(connectionConfigList, connection)) {
				log.debug("find database connection ({})", connection);
			} else {
				log.debug("cannot find database connection ({})", connection);
			}
		});

		if (connectionConfigList.isEmpty()) {
			return Collections.emptyList();
		}

		var connectionInfoList = new ArrayList<ConnectionInfo<MongoClient>>();
		for (var connectionConfig : connectionConfigList) {
			connectionInfoList.add(createConnectionInfo(connectionConfig));
		}
		return connectionInfoList;
	}
	
	/**
	 * 리더가 조회한 설정을 connection 이름 기준으로 중복 없이 누적한다.
	 *
	 * <p>
	 * 여러 리더가 같은 connection을 알고 있으면(예: mongodb-mongoclient와 properties-mongoclient를 함께 사용)
	 * 같은 connection에 MongoClient가 중복 생성되고, registry에서 조회되지 않는 쪽은 프로세스 수명 내내 닫히지 않은 채
	 * 서버측 logical session을 점유한다. 먼저 조회된 리더의 설정을 유지한다.
	 */
	private void addConnectionConfigList(List<MongoClientConnectionConfig> connectionConfigList,
			List<MongoClientConnectionConfig> readConnectionConfigList,
			ConnectionInfoReader<MongoClientConnectionConfig> connectionInfoReader) {
		for (var connectionConfig : readConnectionConfigList) {
			if (connectionConfig == null || !StringUtils.hasText(connectionConfig.getConnection())) {
				log.warn("skip database connection without connection name from reader ({})",
						connectionInfoReader.getReaderKey());
				continue;
			}

			if (containsConnection(connectionConfigList, connectionConfig.getConnection())) {
				log.warn("skip duplicated database connection ({}) from reader ({})", connectionConfig.getConnection(),
						connectionInfoReader.getReaderKey());
				continue;
			}

			connectionConfigList.add(connectionConfig);
		}
	}

	private boolean containsConnection(List<MongoClientConnectionConfig> connectionConfigList, String connection) {
		return connectionConfigList.stream()
			.anyMatch(connectionConfig -> connectionConfig.getConnection().equalsIgnoreCase(connection));
	}

	private ConnectionInfo<MongoClient> createConnectionInfo(MongoClientConnectionConfig connectionConfig) {
		var mongoClient = MongoClients.create(buildMongoClientSettings(connectionConfig));
		this.createdMongoClients.add(mongoClient);
		return new ConnectionInfo<>(new ConnectionInfoKey(getLoaderKey(), connectionConfig.getConnection()),
				mongoClient);
	}
	

	/**
	 * connectionString을 기반으로 MongoClientSettings를 구성한다.
	 *
	 * <p>
	 * connection-info.mongodb.* 의 pool/read/write 설정은 connectionString에 해당 옵션이 명시되지
	 * 않은 경우에만 적용한다(connectionString의 옵션이 우선). pool 설정이 적용되지 않으면 드라이버 기본값
	 * (maxPoolSize=100, maxConnectionIdleTime=무제한)이 사용되어 커넥션/세션이 과도하게 유지된다.
	 */
	protected MongoClientSettings buildMongoClientSettings(MongoClientConnectionConfig connectionConfig) {
		var connectionString = new ConnectionString(connectionConfig.getConnectionString());
		var settingsBuilder = MongoClientSettings.builder().applyConnectionString(connectionString);

		var connectionPoolProperties = this.defaultProperties.getConnectionPool();
		if (connectionPoolProperties != null) {
			settingsBuilder.applyToConnectionPoolSettings(
					poolBuilder -> applyConnectionPool(poolBuilder, connectionString, connectionPoolProperties));
		}

		if (connectionString.getReadPreference() == null) {
			applyQuietly(connectionConfig, "read-preference", this.defaultProperties.getReadPreference(),
					(value) -> settingsBuilder.readPreference(ReadPreference.valueOf(value)));
		}

		if (connectionString.getReadConcern() == null) {
			applyQuietly(connectionConfig, "read-concern-level", this.defaultProperties.getReadConcernLevel(),
					(value) -> settingsBuilder.readConcern(new ReadConcern(ReadConcernLevel.fromString(value))));
		}

		if (connectionString.getWriteConcern() == null) {
			applyWriteConcern(connectionConfig, settingsBuilder);
		}

		if (connectionString.getUuidRepresentation() == null) {
			applyQuietly(connectionConfig, "uuid-representation", this.defaultProperties.getUuidRepresentation(),
					(value) -> settingsBuilder.uuidRepresentation(UuidRepresentation.valueOf(value)));
		}

		// 서버측 currentOp/세션 추적 시 어떤 connection이 만든 클라이언트인지 식별하기 위해 지정한다.
		if (connectionString.getApplicationName() == null) {
			settingsBuilder.applicationName(connectionConfig.getConnection());
		}

		return settingsBuilder.build();
	}

	private void applyConnectionPool(ConnectionPoolSettings.Builder poolBuilder, ConnectionString connectionString,
			MongoDbDefaultProperties.ConnectionPool connectionPoolProperties) {
		if (connectionString.getMaxConnectionPoolSize() == null && connectionPoolProperties.getMaxSize() != null) {
			poolBuilder.maxSize(connectionPoolProperties.getMaxSize());
		}
		if (connectionString.getMinConnectionPoolSize() == null && connectionPoolProperties.getMinSize() != null) {
			poolBuilder.minSize(connectionPoolProperties.getMinSize());
		}
		if (connectionString.getMaxWaitTime() == null && connectionPoolProperties.getMaxWaitTimeMs() != null) {
			poolBuilder.maxWaitTime(connectionPoolProperties.getMaxWaitTimeMs(), TimeUnit.MILLISECONDS);
		}
		if (connectionString.getMaxConnectionLifeTime() == null && connectionPoolProperties.getMaxConnectionLifeTimeMs() != null) {
			poolBuilder.maxConnectionLifeTime(connectionPoolProperties.getMaxConnectionLifeTimeMs(), TimeUnit.MILLISECONDS);
		}
		if (connectionString.getMaxConnectionIdleTime() == null && connectionPoolProperties.getMaxConnectionIdleTimeMs() != null) {
			poolBuilder.maxConnectionIdleTime(connectionPoolProperties.getMaxConnectionIdleTimeMs(), TimeUnit.MILLISECONDS);
		}
	}

	private void applyWriteConcern(MongoClientConnectionConfig connectionConfig, MongoClientSettings.Builder settingsBuilder) {
		var writeConcernProperties = this.defaultProperties.getWriteConcern();
		if (writeConcernProperties == null || !StringUtils.hasText(writeConcernProperties.getW())) {
			return;
		}

		var writeConcern = WriteConcern.valueOf(writeConcernProperties.getW());
		if (writeConcern == null) {
			log.warn("ignore invalid connection-info.mongodb.write-concern.w ({}) for connection ({})",
					writeConcernProperties.getW(), connectionConfig.getConnection());
			return;
		}

		if (writeConcernProperties.getwTimeoutMs() != null) {
			writeConcern = writeConcern.withWTimeout(writeConcernProperties.getwTimeoutMs(), TimeUnit.MILLISECONDS);
		}
		if (writeConcernProperties.getJournal() != null) {
			writeConcern = writeConcern.withJournal(writeConcernProperties.getJournal());
		}

		settingsBuilder.writeConcern(writeConcern);
	}

	/**
	 * 설정값이 비어있으면 건너뛰고, 값이 잘못된 경우 경고만 남긴 뒤 드라이버 기본값을 사용한다.
	 * 설정 오타 하나로 애플리케이션 기동이 실패하지 않도록 하기 위함이다.
	 */
	private void applyQuietly(MongoClientConnectionConfig connectionConfig, String propertyName, String value, Consumer<String> applier) {
		if (!StringUtils.hasText(value)) {
			return;
		}

		try {
			applier.accept(value);
		}
		catch (IllegalArgumentException _) {
			log.warn("ignore invalid connection-info.mongodb.{} ({}) for connection ({})", propertyName, value,
					connectionConfig.getConnection());
		}
	}

	/**
	 * 앱 종료 시 이 로더가 생성한 MongoClient를 모두 닫는다. close()는 서버에 endSessions를 보내 해당 클라이언트의
	 * logical session을 즉시 반납하므로, 재배포/재시작 때 세션이 서버에 남아 TooManyLogicalSessions로 누적되는 것을 막는다.
	 */
	@Override
	public void destroy() {
		for (var mongoClient : this.createdMongoClients) {
			try {
				mongoClient.close();
			}
			catch (Exception exception) {
				log.warn("Failed to close MongoClient on shutdown", exception);
			}
		}
		this.createdMongoClients.clear();
	}

}