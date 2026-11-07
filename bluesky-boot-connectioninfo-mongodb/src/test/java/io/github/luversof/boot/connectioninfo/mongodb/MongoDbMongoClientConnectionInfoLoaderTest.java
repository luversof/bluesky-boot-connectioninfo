package io.github.luversof.boot.connectioninfo.mongodb;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties.ConnectionInfoLoaderProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties.ConnectionInfoReaderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MongoDbMongoClientConnectionInfoLoaderTest {

	private static final Logger log = LoggerFactory.getLogger(MongoDbMongoClientConnectionInfoLoaderTest.class);

	@Test
	@Disabled("localhost:27017 MongoDB가 필요한 수동 확인용 테스트")
	void test() {
		var connectionInfoReaderProperties = new ConnectionInfoReaderProperties();
		connectionInfoReaderProperties.setProperties(
				Map.of("connectionString", "mongodb://localhost:27017", "database", "connection_info_localdev"));

		var connectionInfoLoaderProperties = new ConnectionInfoLoaderProperties();
		connectionInfoLoaderProperties.setEnabled(true);
		connectionInfoLoaderProperties.setConnections(Map.of("connExample", List.of("test1", "test2")));

		var connectionInfoProperties = new ConnectionInfoProperties();
		connectionInfoProperties.getReaders().put("mongodb-mongoclient", connectionInfoReaderProperties);
		connectionInfoProperties.getLoaders().put("mongoclient", connectionInfoLoaderProperties);

		MongoDbMongoClientConnectionInfoReader connectionInfoReader = new MongoDbMongoClientConnectionInfoReader(
				connectionInfoProperties);

		var mongoDbMongoClientConnectionInfoLoader = new MongoDbMongoClientConnectionInfoLoader(
				connectionInfoProperties, List.of(connectionInfoReader));
		mongoDbMongoClientConnectionInfoLoader.load();
		log.debug("Test :");
	}
}
