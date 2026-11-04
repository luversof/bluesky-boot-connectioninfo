package io.github.luversof.boot.connectioninfo;

import java.util.Map;
import java.util.Objects;

public class DataSourceConnectionConfig implements ConnectionConfig {

	private String connection;

	private String url;

	private String username;

	private String password;

	private Map<String, Object> extradata;

	public String getConnection() {
		return connection;
	}

	public void setConnection(String connection) {
		this.connection = connection;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
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

	public Map<String, Object> getExtradata() {
		return extradata;
	}

	public void setExtradata(Map<String, Object> extradata) {
		this.extradata = extradata;
	}

	@Override
	public String toString() {
		return "DataSourceConnectionConfig(connection=" + this.getConnection() + ", url=" + this.getUrl()
				+ ", username=" + this.getUsername() + ", password=" + (this.getPassword() == null ? null : "****") + ", extradata="
				+ this.getExtradata() + ")";
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this)
			return true;
		if (!(o instanceof DataSourceConnectionConfig))
			return false;
		final DataSourceConnectionConfig other = (DataSourceConnectionConfig) o;
		if (!other.canEqual((Object) this))
			return false;
		final Object this$connection = this.getConnection();
		final Object other$connection = other.getConnection();
		if (!Objects.equals(this$connection, other$connection))
			return false;
		final Object this$url = this.getUrl();
		final Object other$url = other.getUrl();
		if (!Objects.equals(this$url, other$url))
			return false;
		final Object this$username = this.getUsername();
		final Object other$username = other.getUsername();
		if (!Objects.equals(this$username, other$username))
			return false;
		final Object this$password = this.getPassword();
		final Object other$password = other.getPassword();
		if (!Objects.equals(this$password, other$password))
			return false;
		final Object this$extradata = this.getExtradata();
		final Object other$extradata = other.getExtradata();
		if (!Objects.equals(this$extradata, other$extradata))
			return false;
		return true;
	}

	protected boolean canEqual(final Object other) {
		return other instanceof DataSourceConnectionConfig;
	}

	@Override
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object $connection = this.getConnection();
		result = result * PRIME + ($connection == null ? 43 : $connection.hashCode());
		final Object $url = this.getUrl();
		result = result * PRIME + ($url == null ? 43 : $url.hashCode());
		final Object $username = this.getUsername();
		result = result * PRIME + ($username == null ? 43 : $username.hashCode());
		final Object $password = this.getPassword();
		result = result * PRIME + ($password == null ? 43 : $password.hashCode());
		final Object $extradata = this.getExtradata();
		result = result * PRIME + ($extradata == null ? 43 : $extradata.hashCode());
		return result;
	}

}