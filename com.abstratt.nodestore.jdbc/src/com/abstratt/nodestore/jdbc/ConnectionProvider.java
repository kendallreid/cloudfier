package com.abstratt.nodestore.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.eclipse.core.runtime.Assert;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Provides connections from a datasource.
 * If a connection is required multiple times in a thread, all requests end up with the same connection
 * (only one connection in use per thread).
 * 
 * Connections must be held for as little as possible, and be returned when no longer in use.
 */
public class ConnectionProvider {
	private Connection connection;
	private AtomicInteger level = new AtomicInteger();
	private DataSource dataSource;
	private String databaseName;
	public ConnectionProvider() {
		PGSimpleDataSource pgDataSource = new PGSimpleDataSource();
		//TODO need a way to pass properties into node store factory
		databaseName = System.getProperty("cloudfier.database.name", "cloudfier");
		pgDataSource.setDatabaseName(databaseName);
		String username = System.getProperty("cloudfier.database.username", "cloudfier");
		pgDataSource.setUser(username);
		this.dataSource = pgDataSource;
	}
	
	public Connection acquireConnection() throws SQLException {
		//System.out.println("acquireConnection - " + level.get());
		boolean firstRequest = level.getAndIncrement() == 0;
		Assert.isLegal((connection == null) == firstRequest, "First? " + firstRequest);
		if (firstRequest) {
			connection = dataSource.getConnection();
			connection.setAutoCommit(false);
		}
		return connection;
	}
	
	public void releaseConnection(boolean success) throws SQLException {
		//System.out.println("releaseConnection - " + level.get());
		if (level.get() == 0) {
			Assert.isTrue(this.connection == null);
			// someone being overly zealous
			return;
		}
		Assert.isTrue(level.get() > 0);
		boolean lastRelease = level.decrementAndGet() == 0;
		if (lastRelease) {
			try {
				if (success) {
					commit();
				} else {
					rollback();
				}
			} finally { 
				Connection tmpConnection = this.connection;
				this.connection = null;
				tmpConnection.close();
			}
		}
	}
	
	public void rollback() throws SQLException {
		Assert.isTrue(this.connection != null);
		JDBCNodeStore.logSQLStatement(databaseName + " - rolling back");
		this.connection.rollback();
	}
	
	public void commit() throws SQLException {
		Assert.isTrue(this.connection != null);
		JDBCNodeStore.logSQLStatement(databaseName + " - committing ");
		this.connection.commit();
	}
	
	public boolean hasConnection() {
		return level.get() > 0;
	}

}