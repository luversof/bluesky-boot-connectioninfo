package io.github.luversof.boot.connectioninfo.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mongodb.ConnectionString;

import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.MongoClientConnectionConfig;

/** 실제 MongoDB 없이 돈다. MongoClient 는 처음 쓸 때 접속하므로 만들고 닫기만 한다. */
class MongoConnectionInfoTests {

	@Test
	void oneClientPerConnectionEvenWhenTwoReadersKnowIt() {
		// 예전에는 중복을 거른 뒤 addAll 로 다시 넣어 connection 마다 MongoClient 가 둘씩 생겼다
		var loader = new MongoDbMongoClientConnectionInfoLoader(new ConnectionInfoProperties(),
				List.of(reader("r1", config("a"), config("b")), reader("r2", config("a"))));
		try {
			var connectionInfoList = loader.load(List.of("a", "b"));

			assertThat(connectionInfoList).extracting(info -> info.getKey().connectionKey()).containsExactly("a", "b");
		} finally {
			loader.destroy();
		}
	}

	@Test
	void destroyClosesCreatedClients() {
		var loader = new MongoDbMongoClientConnectionInfoLoader(new ConnectionInfoProperties(), List.of(reader("r1", config("a"))));
		var client = loader.load(List.of("a")).getFirst().getConnection();

		loader.destroy();

		// 닫힌 클라이언트는 서버를 찾지 않고 바로 실패한다(열려 있으면 서버 선택을 30초 기다린다)
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.listDatabaseNames().first())
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void specialCharactersInCredentialsSurviveTheConnectionString() {
		var reader = new PropertiesMongoClientConnectionInfoReader(new ConnectionInfoProperties(), new MongoDbDefaultProperties(),
				new MongoDbConnectionMapProperties());
		var connConfig = new MongoDbConnectionMapProperties.MongoConnectionConfig();
		connConfig.setHosts("h1:27017,h2:27017");
		connConfig.setDatabase("db");

		var connectionString = new ConnectionString(reader.buildConnectionString(connConfig, "us@r", "p@ss:w/rd%2 +"));

		assertThat(connectionString.getUsername()).isEqualTo("us@r");
		assertThat(new String(connectionString.getPassword())).isEqualTo("p@ss:w/rd%2 +");
		assertThat(connectionString.getHosts()).containsExactly("h1:27017", "h2:27017");
		assertThat(connectionString.getCredential().getSource()).isEqualTo("admin");
	}

	@Test
	void toStringHidesPassword() {
		var config = config("a");
		config.setPassword("secret");

		assertThat(config.toString()).doesNotContain("secret").contains("password=****");
	}

	private static MongoClientConnectionConfig config(String connection) {
		var config = new MongoClientConnectionConfig();
		config.setConnection(connection);
		config.setConnectionString("mongodb://localhost:1/" + connection);
		return config;
	}

	private static ConnectionInfoReader<MongoClientConnectionConfig> reader(String key, MongoClientConnectionConfig... configs) {
		return new ConnectionInfoReader<>() {
			@Override
			public String getReaderKey() {
				return key;
			}

			@Override
			public List<MongoClientConnectionConfig> readConnectionConfigList(List<String> connectionList) {
				return List.of(configs).stream().filter(c -> connectionList.contains(c.getConnection())).toList();
			}
		};
	}
}
