package io.github.luversof.boot.connectioninfo;

import java.util.Objects;

import org.bson.codecs.pojo.annotations.BsonProperty;

public class MongoClientConnectionConfig implements ConnectionConfig {

	@BsonProperty("connection")
	private String connection;

	@BsonProperty("connectionString")
	private String connectionString;

	@BsonProperty("database")
	private String database;

	@BsonProperty("userName")
	private String userName;

	@BsonProperty("password")
	private String password;

	public String getConnection() {
		return connection;
	}

	public void setConnection(String connection) {
		this.connection = connection;
	}

	public String getConnectionString() {
		return connectionString;
	}

	public void setConnectionString(String connectionString) {
		this.connectionString = connectionString;
	}

	public String getDatabase() {
		return database;
	}

	public void setDatabase(String database) {
		this.database = database;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public String toString() {
		return "MongoClientConnectionConfig(connection=" + this.getConnection() + ", connectionString="
				+ this.getConnectionString() + ", database=" + this.getDatabase() + ", userName=" + this.getUserName()
				+ ", password=" + (this.getPassword() == null ? null : "****") + ")";
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this)
			return true;
		if (!(o instanceof MongoClientConnectionConfig))
			return false;
		final MongoClientConnectionConfig other = (MongoClientConnectionConfig) o;
		if (!other.canEqual((Object) this))
			return false;
		final Object this$connection = this.getConnection();
		final Object other$connection = other.getConnection();
		if (!Objects.equals(this$connection, other$connection))
			return false;
		final Object this$connectionString = this.getConnectionString();
		final Object other$connectionString = other.getConnectionString();
		if (!Objects.equals(this$connectionString, other$connectionString))
			return false;
		final Object this$database = this.getDatabase();
		final Object other$database = other.getDatabase();
		if (!Objects.equals(this$database, other$database))
			return false;
		final Object this$userName = this.getUserName();
		final Object other$userName = other.getUserName();
		if (!Objects.equals(this$userName, other$userName))
			return false;
		final Object this$password = this.getPassword();
		final Object other$password = other.getPassword();
		if (!Objects.equals(this$password, other$password))
			return false;
		return true;
	}

	protected boolean canEqual(final Object other) {
		return other instanceof MongoClientConnectionConfig;
	}

	@Override
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object $connection = this.getConnection();
		result = result * PRIME + ($connection == null ? 43 : $connection.hashCode());
		final Object $connectionString = this.getConnectionString();
		result = result * PRIME + ($connectionString == null ? 43 : $connectionString.hashCode());
		final Object $database = this.getDatabase();
		result = result * PRIME + ($database == null ? 43 : $database.hashCode());
		final Object $userName = this.getUserName();
		result = result * PRIME + ($userName == null ? 43 : $userName.hashCode());
		final Object $password = this.getPassword();
		result = result * PRIME + ($password == null ? 43 : $password.hashCode());
		return result;
	}

}