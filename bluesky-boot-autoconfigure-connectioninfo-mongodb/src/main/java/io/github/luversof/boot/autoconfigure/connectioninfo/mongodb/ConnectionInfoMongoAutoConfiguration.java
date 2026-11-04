package io.github.luversof.boot.autoconfigure.connectioninfo.mongodb;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.mongodb.client.MongoClient;

import io.github.luversof.boot.autoconfigure.connectioninfo.ConnectionInfoAutoConfiguration;
import io.github.luversof.boot.connectioninfo.ConnectionInfo;
import io.github.luversof.boot.connectioninfo.ConnectionInfoLoader;
import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.ConnectionInfoRegistry;
import io.github.luversof.boot.connectioninfo.MongoClientConnectionConfig;
import io.github.luversof.boot.connectioninfo.mongodb.MongoDbConnectionMapProperties;
import io.github.luversof.boot.connectioninfo.mongodb.MongoDbDefaultProperties;
import io.github.luversof.boot.connectioninfo.mongodb.MongoDbMongoClientConnectionInfoLoader;
import io.github.luversof.boot.connectioninfo.mongodb.MongoDbMongoClientConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.mongodb.PropertiesMongoClientConnectionInfoReader;

@AutoConfiguration(
    value = "blueskyBootConnectionInfoMongoAutoConfiguration",
    before = {MongoAutoConfiguration.class, ConnectionInfoAutoConfiguration.class})
@ConditionalOnClass(MongoClient.class)
@EnableConfigurationProperties({
  MongoDbDefaultProperties.class,
  MongoDbConnectionMapProperties.class
})
public class ConnectionInfoMongoAutoConfiguration {

  @Bean
  @ConditionalOnProperty(
      prefix = "bluesky-boot.connection-info.readers",
      name = "mongo-mongoclient.enabled",
      havingValue = "true")
  MongoDbMongoClientConnectionInfoReader mongoDbMongoClientConnectionInfoReader(
      ConnectionInfoProperties connectionInfoProperties) {
    return new MongoDbMongoClientConnectionInfoReader(connectionInfoProperties);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "bluesky-boot.connection-info.readers",
      name = "properties-mongoclient.enabled",
      havingValue = "true")
  PropertiesMongoClientConnectionInfoReader propertiesMongoClientConnectionInfoReader(
      ConnectionInfoProperties connectionInfoProperties,
      MongoDbDefaultProperties defaultProperties,
      MongoDbConnectionMapProperties connectionMapProperties) {
    return new PropertiesMongoClientConnectionInfoReader(
        connectionInfoProperties, defaultProperties, connectionMapProperties);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "bluesky-boot.connection-info.loaders",
      name = "mongoclient.enabled",
      havingValue = "true")
  MongoDbMongoClientConnectionInfoLoader mongoDbMongoClientConnectionInfoLoader(
      ConnectionInfoProperties connectionInfoProperties,
      List<ConnectionInfoReader<MongoClientConnectionConfig>> connectionInfoReaderList,
      MongoDbDefaultProperties defaultProperties) {
    return new MongoDbMongoClientConnectionInfoLoader(
        connectionInfoProperties, connectionInfoReaderList, defaultProperties);
  }

  @Bean
  <T extends MongoClient, C extends MongoClientConnectionConfig>
      ConnectionInfoRegistry<T> mongoClientConnectionInfoRegistry(
          List<ConnectionInfoLoader<T, C>> connectionInfoLoaderList) {
    // LazyLoad(ConnectionInfoUtil.getConnection)로 런타임에 추가될 수 있어 조회/추가가 동시에 일어난다.
    var connectionInfoList = new CopyOnWriteArrayList<ConnectionInfo<T>>();
    connectionInfoLoaderList.forEach(
        connectionInfoLoader -> connectionInfoList.addAll(connectionInfoLoader.load()));
    return () -> connectionInfoList;
  }
}
