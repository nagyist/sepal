package org.openforis.sepal.transaction

import groovy.sql.Sql
import org.openforis.sepal.util.lifecycle.Stoppable

import javax.sql.DataSource
import java.sql.Connection

class SqlConnectionManager implements SqlConnectionProvider, TransactionManager, Stoppable {
    final DataSource dataSource
    private final ThreadLocal<Connection> connectionHolder = new ThreadLocal<Connection>()
    private final ThreadLocal<List<Closure>> afterCommitCallbacksHolder = new ThreadLocal<List<Closure>>() {
        protected List<Closure> initialValue() { [] }
    }

    SqlConnectionManager(DataSource dataSource) {
        this.dataSource = dataSource
    }

    public <T> T withTransaction(Closure<T> closure) {
        def connection = null
        boolean newTransaction = !isTransactionRunning()
        try {
            connection = openConnection()
            def result = closure.call()
            if (newTransaction) {
                connection.commit()
                notifyListeners(result)
            }
            return result
        } catch (Exception e) {
            connection.rollback()
            throw e
        } finally {
            if (newTransaction) {
                connectionHolder.remove()
                afterCommitCallbacksHolder.remove()
                connection?.close()
            }
        }
    }

    void registerAfterCommitCallback(Closure callback) {
        afterCommitCallbacksHolder.get() << callback
    }

    boolean isTransactionRunning() {
        connectionHolder.get() != null
    }

    private <T> void notifyListeners(T result) {
        afterCommitCallbacksHolder.get().each { it.call(result) }
    }

    private Connection openConnection() {
        def connection = connectionHolder.get()
        if (connection) return connection
        connection = dataSource.connection
        connection.autoCommit = false
        connectionHolder.set(connection)
        return connection
    }

    Sql getSql() {
        def connection = connectionHolder.get()
        connection ? new Sql(connection) : new Sql(dataSource)
    }

    Connection getConnection() {
        connectionHolder.get() ?: dataSource.connection
    }

    void stop() {
        def connection = connectionHolder.get()
        if (connection) {
            connection.rollback()
            connection.close()
        }
    }
}
