package io.github.luversof.boot.connectioninfo.mongodb;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.pojo.PojoCodecProvider;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import io.github.luversof.boot.connectioninfo.ConnectionInfoProperties;
import io.github.luversof.boot.connectioninfo.ConnectionInfoReader;
import io.github.luversof.boot.connectioninfo.MongoClientConnectionConfig;
import io.github.luversof.boot.security.crypto.factory.TextEncryptorFactories;

public class MongoDbMongoClientConnectionInfoReader
    implements ConnectionInfoReader<MongoClientConnectionConfig> {

  protected String readerKey = "mongodb-mongoclient";

  protected final ConnectionInfoProperties connectionInfoProperties;

  public MongoDbMongoClientConnectionInfoReader(ConnectionInfoProperties connectionInfoProperties) {
    this.connectionInfoProperties = connectionInfoProperties;
  }

  @Override
  public String getReaderKey() {
    return readerKey;
  }

  @Override
  public List<MongoClientConnectionConfig> readConnectionConfigList(List<String> connectionList) {
    try (var loaderMongoClient = getLoaderMongoClient()) {
      var mongoDatabase = loaderMongoClient.getDatabase(getConfigProperties("database"));
      var mongoCollection = mongoDatabase.getCollection("MongoClientConnectionConfig");
      var query = new Document("connection", new Document("$in", connectionList));
      var mongoDbMongoClientConnectionConfigList =
          mongoCollection.find(query, MongoClientConnectionConfig.class);
      return mongoDbMongoClientConnectionConfigList.into(new ArrayList<>());
    }
  }

  private MongoClient getLoaderMongoClient() {
    var pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
    var pojoCodecRegistry =
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(pojoCodecProvider));

    var settings =
        MongoClientSettings.builder()
            .applyConnectionString(
                new com.mongodb.ConnectionString(getConfigProperties("connectionString")))
            .codecRegistry(pojoCodecRegistry)
            .build();

    return MongoClients.create(settings);
  }

  private String getConfigProperties(String key) {
    var encryptor = TextEncryptorFactories.getDelegatingTextEncryptor();
    var loaderProperties =
        connectionInfoProperties.getReaders().get(getReaderKey()).getProperties();
    return encryptor.decrypt(loaderProperties.get(key));
  }
}
