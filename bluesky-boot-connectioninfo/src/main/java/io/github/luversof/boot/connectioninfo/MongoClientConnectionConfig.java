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
		if (!(o instanceof MongoClientConnectionConfig other))
			return false;
		return other.canEqual(this)
				&& Objects.equals(this.getConnection(), other.getConnection())
				&& Objects.equals(this.getConnectionString(), other.getConnectionString())
				&& Objects.equals(this.getDatabase(), other.getDatabase())
				&& Objects.equals(this.getUserName(), other.getUserName())
				&& Objects.equals(this.getPassword(), other.getPassword());
	}

	protected boolean canEqual(final Object other) {
		return other instanceof MongoClientConnectionConfig;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.getConnection(), this.getConnectionString(), this.getDatabase(), this.getUserName(),
				this.getPassword());
	}

}