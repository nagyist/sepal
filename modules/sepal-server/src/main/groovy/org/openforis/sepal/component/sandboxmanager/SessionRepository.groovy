package org.openforis.sepal.component.sandboxmanager

import groovy.sql.Sql
import groovy.transform.ToString
import org.openforis.sepal.hostingservice.Status
import org.openforis.sepal.transaction.SqlConnectionManager
import org.openforis.sepal.util.Clock

import java.time.Duration

import static org.openforis.sepal.hostingservice.Status.*

interface SessionRepository {
    List<SandboxSession> findWithStatus(String username, Collection<Status> statuses)

    List<SandboxSession> findStartingSessions()

    void stopAllTimedOut(Date updatedBefore, Closure callback)

    SandboxSession getById(long sessionId)

    SandboxSession creating(String username, String instanceType)

    SandboxSession deployed(SandboxSession session)

    void alive(long sessionId, Date lastUpdated)

    void terminate(Closure<Boolean> callback)

    void updateStatusInNewTransaction(long id, Status status)

    void update(SandboxSession sandboxSession)

    Map<String, Double> hoursByInstanceType(String username, Date since)
}

@ToString
class JdbcSessionRepository implements SessionRepository {
    private final SqlConnectionManager connectionManager
    private final Clock clock

    JdbcSessionRepository(SqlConnectionManager connectionManager, Clock clock) {
        this.connectionManager = connectionManager
        this.clock = clock
    }

    List<SandboxSession> findWithStatus(String username, Collection<Status> statuses) {
        def statusesPlaceHolders = (['?'] * statuses.size()).join(', ')
        def args = [username] + statuses.collect { it.name() }
        sql.rows("""
                SELECT id, username, instance_id, instance_type, host, port, status, creation_time, update_time, termination_time
                FROM sandbox_session
                WHERE username = ?
                AND status in ($statusesPlaceHolders)
                """ as String, args).collect { row ->
            toSession(row)
        }
    }

    void stopAllTimedOut(Date updatedBefore, Closure callback) {
        sql.eachRow('''
                SELECT id, username, instance_id, instance_type, host, port, status, creation_time, update_time, termination_time
                FROM sandbox_session
                WHERE (status = ? OR status = ?)
                AND update_time < ?
                ''', [ACTIVE.name(), STARTING.name(), updatedBefore]) { row ->
            callback.call(toSession(row))
            doUpdateStatus(row.id, STOPPED, sql)
        }
    }

    void updateStatusInNewTransaction(long id, Status status) {
        def sql = new Sql(connectionManager.dataSource)
        sql.withTransaction {
            doUpdateStatus(id, status, sql)
        }
    }

    void update(SandboxSession session) {
        session.with {
            sql.executeUpdate('''
                    UPDATE sandbox_session
                    SET instance_id = ?, instance_type = ?, host = ?, port = ?, status = ?,
                        creation_time = ?, update_time = ?, termination_time = ?
                    WHERE id = ?''',
                    [instanceId, instanceType, host, port, status.name(), creationTime, updateTime, terminationTime, id])
        }
    }

    void doUpdateStatus(long id, Status status, Sql sql) {
        sql.executeUpdate('''
                    UPDATE sandbox_session
                    SET status = ?, update_time = ?
                    WHERE id = ?''', [status.name(), clock.now(), id])
    }

    List<SandboxSession> findStartingSessions() {
        sql.rows('''
                SELECT id, username, instance_id, instance_type, host, port, status, creation_time, update_time, termination_time
                FROM sandbox_session
                WHERE status = ?
                ''', [STARTING.name()]).collect {
            toSession(it)
        }
    }

    SandboxSession getById(long sessionId) {
        def row = sql.firstRow('''
                SELECT id, username, instance_id, instance_type, instance_type, host, port, status, creation_time, update_time, termination_time
                FROM sandbox_session
                WHERE id = ?''', [sessionId])
        if (row == null)
            throw new NotFound("$sessionId: session not found")
        return toSession(row)
    }

    SandboxSession creating(String username, String instanceType) {
        // Need to run in separate transaction, and immediately commit
        // To make sure we have a record of an attempt of creating a sandbox, even if the server crashes
        def now = clock.now()
        def status = PENDING
        SandboxSession session = null
        def sql = new Sql(connectionManager.dataSource)
        sql.withTransaction {
            def generated = sql.executeInsert('''
                INSERT INTO sandbox_session(username, instance_type, status, creation_time, update_time)
                VALUES(?, ?, ?, ?, ?)''', [username, instanceType, status.name(), now, now])
            def id = generated[0][0] as long
            session = new SandboxSession(
                    id: id,
                    username: username,
                    instanceType: instanceType,
                    status: status,
                    creationTime: now,
                    updateTime: now
            )
        }
        return session
    }

    SandboxSession deployed(SandboxSession session) {
        sql.executeUpdate('''
                UPDATE sandbox_session
                SET status = ?, instance_id = ?, host = ?, port = ?, update_time = ?
                WHERE id = ?''', [session.status.name(), session.instanceId, session.host, session.port, clock.now(), session.id])
        return session
    }

    void alive(long sessionId, Date lastUpdated) {
        sql.executeUpdate('UPDATE sandbox_session SET update_time = ? WHERE id = ?', [lastUpdated, sessionId])
    }

    void terminate(Closure<Boolean> callback) {
        sql.eachRow('''
                SELECT DISTINCT instance_id, instance_type
                FROM sandbox_session
                WHERE status = ? AND instance_id NOT IN (
                    SELECT DISTINCT instance_id
                    FROM sandbox_session
                    WHERE status != ?
                )''', [STOPPED.name(), STOPPED.name()]) {
            def instanceId = it.instance_id
            callback.call(instanceId, it.instance_type)
            sql.executeUpdate('''
                        UPDATE sandbox_session
                        SET status = ?, termination_time = ?
                        WHERE instance_id = ?''',
                    [TERMINATED.name(), clock.now(), instanceId])
        }
    }

    Map<String, Double> hoursByInstanceType(String username, Date since) {
        def hoursByInstanceType = [:]
        def rows = sql.rows('''
                SELECT creation_time, update_time, instance_type, status
                FROM sandbox_session
                WHERE status IN (?, ?, ?) OR (status NOT IN (?, ?, ?) AND creation_time >= ?)''',
                [PENDING.name(), ACTIVE.name(), STARTING.name(), PENDING.name(), ACTIVE.name(), STARTING.name(), since])
        rows.each { row ->
            def from = [since, row.creation_time].max()
            def to = row.status in [PENDING.name(), ACTIVE.name(), STARTING.name()] ? clock.now() : row.update_time
            def duration = Duration.between(from.toInstant(), to.toInstant())
            if (!hoursByInstanceType.containsKey(row.instance_type))
                hoursByInstanceType[row.instance_type] = 0
            hoursByInstanceType[row.instance_type] += Math.ceil(duration.toMinutes() / 60d)
        }
        return hoursByInstanceType
    }

    private SandboxSession toSession(row) {
        new SandboxSession(
                id: row.id,
                username: row.username,
                instanceId: row.instance_id,
                instanceType: row.instance_type,
                host: row.host,
                port: row.port,
                status: row.status,
                creationTime: row.creation_time,
                updateTime: row.update_time,
                terminationTime: row.termination_time
        )
    }

    private Sql getSql() {
        connectionManager.sql
    }

    class NotFound extends RuntimeException {
        NotFound(String message) {
            super(message)
        }
    }
}
