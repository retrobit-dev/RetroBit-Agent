package dev.retrobit.agent.agent

import dev.retrobit.agent.data.ProjectSqlPolicy as AgentSqlPolicy

import org.junit.Assert.*
import org.junit.Test

class AgentSqlPolicyTest {
    @Test fun `ordinary single statement SQL permitted`() {
        listOf("SELECT * FROM todos;", "INSERT INTO todos(title) VALUES('a;b')", "CREATE TABLE todos(id INTEGER)", "-- hi\nUPDATE todos SET title='ATTACH';", "WITH t AS (SELECT 1) SELECT * FROM t").forEach(AgentSqlPolicy::validate)
    }
    @Test fun `external file access and internal pragmas blocked`() {
        listOf("ATTACH DATABASE '/tmp/a' AS a", "PRAGMA\twritable_schema=1", "VACUUM INTO '/tmp/x'", "SELECT load_extension('/tmp/x')", "/*x*/ DETACH a", "SELECT \"load_extension\"('/tmp/x')").forEach { assertTrue(it, runCatching { AgentSqlPolicy.validate(it) }.isFailure) }
    }
    @Test fun `multiple statements and recursive expansion blocked`() {
        listOf("SELECT 1; DROP TABLE todos", "CREATE TABLE x(id); ATTACH 'x' AS y", "WITH RECURSIVE a AS (SELECT 1) SELECT * FROM a").forEach { assertTrue(it, runCatching { AgentSqlPolicy.validate(it) }.isFailure) }
    }
    @Test fun `truncated quotes or comments blocked`() {
        listOf("SELECT 'x", "SELECT /*x", "SELECT \"x").forEach { assertTrue(runCatching { AgentSqlPolicy.validate(it) }.isFailure) }
    }
    @Test fun `defaults accept literals not executable expressions`() {
        listOf("NULL", "CURRENT_TIMESTAMP", "1", "-3.5", "'a''b'").forEach { assertTrue(it, AgentSqlPolicy.defaultLiteral(it)) }
        listOf("1); DROP TABLE x;--", "(SELECT x)", "randomblob(999999)").forEach { assertFalse(it, AgentSqlPolicy.defaultLiteral(it)) }
    }
}
