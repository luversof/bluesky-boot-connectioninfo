package io.github.luversof.boot.connectioninfo.jdbc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import io.github.luversof.boot.connectioninfo.ConnectionInfo;
import io.github.luversof.boot.connectioninfo.ConnectionInfoLoader;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.DataSourceConnectionConfig;

/**
 * Used when obtaining DataSource by loading connectionInfo from DB
 * 
 * @author bluesky
 * 
 * @param <T> The type of DataSource to be loaded via Loader
 */
public abstract class AbstractDataSourceConnectionInfoLoader<T extends DataSource, C extends DataSourceConnectionConfig>
		implements ConnectionInfoLoader<T, C>, DisposableBean {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	protected final ConnectionInfoProperties connectionInfoProperties;

	protected final List<ConnectionInfoReader<C>> connectionInfoReaderList;
	
	// 이 로더가 생성한 HikariDataSource들. 앱 종료 시 close()로 커넥션 풀을 반납하기 위해 추적한다.
	protected final List<T> createdDataSources = new CopyOnWriteArrayList<>();

	protected AbstractDataSourceConnectionInfoLoader(ConnectionInfoProperties connectionInfoProperties,
			List<ConnectionInfoReader<C>> connectionInfoReaderList) {
		this.connectionInfoProperties = connectionInfoProperties;
		this.connectionInfoReaderList = connectionInfoReaderList;
	}

	public List<ConnectionInfoReader<C>> getConnectionInfoReaderList() {
		return connectionInfoReaderList;
	}

	@Override
	public List<ConnectionInfo<T>> load() {
		if (connectionInfoProperties == null
				|| connectionInfoProperties.getLoaders() == null
				|| !connectionInfoProperties.getLoaders().containsKey(getLoaderKey())
				|| CollectionUtils
						.isEmpty(connectionInfoProperties.getLoaders().get(getLoaderKey()).getConnections())) {
			return Collections.emptyList();
		}

		List<String> connectionList = connectionInfoProperties.getLoaders().get(getLoaderKey()).getConnections()
				.values().stream().flatMap(List::stream).distinct().toList();

		return load(connectionList);
	}

	@Override
	public List<ConnectionInfo<T>> load(List<String> connectionList) {

		log.debug("connectionInfoReaderKeyList : {}",
				getConnectionInfoReaderList().stream().map(reader -> reader.getReaderKey()).toList());

		var connectionConfigList = new ArrayList<C>();
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

		if (CollectionUtils.isEmpty(connectionConfigList)) {
			return Collections.emptyList();
		}

		var connectionInfoList = new ArrayList<ConnectionInfo<T>>();
		for (var connectionConfig : connectionConfigList) {
			connectionInfoList.add(createConnectionInfo(connectionConfig));
		}

		return connectionInfoList;
	}
	
	/**
	 * 리더가 조회한 설정을 connection 이름 기준으로 중복 없이 누적한다.
	 *
	 * <p>
	 * 여러 리더가 같은 connection을 알고 있으면(예: mysql/mariadb 리더를 함께 사용) 같은 connection에
	 * HikariDataSource가 중복 생성되고, registry에서 조회되지 않는 쪽은 커넥션 풀째로 닫히지 않은 채 남는다.
	 * 먼저 조회된 리더의 설정을 유지한다.
	 */
	private void addConnectionConfigList(List<C> connectionConfigList,
			List<C> readConnectionConfigList,
			ConnectionInfoReader<C> connectionInfoReader) {
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

	private boolean containsConnection(List<C> connectionConfigList, String connection) {
		return connectionConfigList.stream()
			.anyMatch(connectionConfig -> connectionConfig.getConnection().equalsIgnoreCase(connection));
	}

	protected abstract ConnectionInfo<T> createConnectionInfo(DataSourceConnectionConfig connectionConfig);

	/**
	 * 앱 종료 시 이 로더가 만든 커넥션 풀을 닫는다. 예전에는 모으기만 하고 닫지 않아 재시작·컨텍스트 재생성 때마다 풀이 남았다.
	 */
	@Override
	public void destroy() {
		for (var dataSource : this.createdDataSources) {
			if (dataSource instanceof AutoCloseable closeable) {
				try {
					closeable.close();
				}
				catch (Exception exception) {
					log.warn("Failed to close DataSource on shutdown", exception);
				}
			}
		}
		this.createdDataSources.clear();
	}

}
