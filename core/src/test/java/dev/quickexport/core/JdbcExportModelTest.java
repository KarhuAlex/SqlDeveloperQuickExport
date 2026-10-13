package dev.quickexport.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcExportModelTest {

    private Connection conn;

    @BeforeEach
    void open() throws SQLException {
        conn = DriverManager.getConnection("jdbc:h2:mem:qe" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    }

    @AfterEach
    void close() throws SQLException {
        conn.close();
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute(sql);
        }
    }

    @Test
    void readsPlainJavaTypes() throws Exception {
        exec("create table t (id numeric(20,4), name varchar(50), d date, ts timestamp, flag boolean,"
                + " dbl double precision, tz timestamp with time zone, raw varbinary(10))");
        exec("insert into t values (12345678901234.5678, 'Åke', date '2024-01-31', timestamp '2024-01-31 13:45:10.5',"
                + " true, 1.5, timestamp with time zone '2024-03-01 10:00:00+02:00', X'0102')");
        exec("insert into t values (null, null, null, null, null, null, null, null)");

        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select * from t order by id nulls last")) {
            JdbcExportModel m = new JdbcExportModel(rs, 100);
            assertEquals(8, m.getColumnCount());
            assertEquals("ID", m.getColumnName(0));
            try (ExportModel.RowCursor c = m.openRows()) {
                assertTrue(c.next());
                assertEquals(new BigDecimal("12345678901234.5678"), c.getValue(0));
                assertEquals("Åke", c.getValue(1));
                assertEquals(java.sql.Timestamp.valueOf("2024-01-31 00:00:00"), c.getValue(2));
                assertEquals(Timestamp.valueOf("2024-01-31 13:45:10.5"), c.getValue(3));
                assertEquals(Boolean.TRUE, c.getValue(4));
                assertEquals(1.5d, c.getValue(5));
                assertEquals(OffsetDateTime.parse("2024-03-01T10:00:00+02:00"), c.getValue(6));
                assertEquals(2, ((byte[]) c.getValue(7)).length);
                assertTrue(c.next());
                for (int i = 0; i < 8; i++) {
                    assertNull(c.getValue(i), "column " + i + " must read as null");
                }
                assertFalse(c.next());
            }
        }
    }

    @Test
    void longClobIsTruncatedWithMarkerAndCounted() throws Exception {
        exec("create table t (c clob, b blob)");
        exec("insert into t values ('" + "x".repeat(50) + "', X'00112233')");
        exec("insert into t values ('short', null)");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select * from t order by length(c) desc")) {
            JdbcExportModel m = new JdbcExportModel(rs, 10);
            try (ExportModel.RowCursor c = m.openRows()) {
                assertTrue(c.next());
                String text = (String) c.getValue(0);
                assertTrue(text.startsWith("x".repeat(10) + "\u2026[truncated, 50 characters in total]"), text);
                assertEquals("[BLOB 4 bytes]", c.getValue(1));
                assertTrue(c.next());
                assertEquals("short", c.getValue(0));
                assertNull(c.getValue(1));
            }
            assertEquals(1, m.getTruncatedLobs());
        }
    }

    @Test
    void worksEndToEndThroughBothWriters() throws Exception {
        exec("create table t (id int, name varchar(20), created timestamp)");
        for (int i = 1; i <= 2500; i++) {
            exec("insert into t values (" + i + ", 'name;" + i + "', timestamp '2024-01-31 13:45:10')");
        }
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select * from t order by id")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ExportResult r = new CsvWriter(ExportSettings.defaults()).write(new JdbcExportModel(rs, 100), out, ExportControl.NONE);
            assertEquals(2500, r.rows());
            String csv = new String(out.toByteArray(), StandardCharsets.UTF_8);
            assertTrue(csv.contains("ID;NAME;CREATED\r\n1;\"name;1\";2024-01-31 13:45:10\r\n"));
        }
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select * from t order by id")) {
            ExportResult r = new XlsxWriter().write(new JdbcExportModel(rs, 100), new ByteArrayOutputStream(), ExportControl.NONE);
            assertEquals(2500, r.rows());
        }
    }

    @Test
    void sqlErrorsSurfaceAsIoExceptionWithColumnContext() throws Exception {
        exec("create table t (id int)");
        exec("insert into t values (1)");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select id from t")) {
            JdbcExportModel m = new JdbcExportModel(rs, 10);
            try (ExportModel.RowCursor c = m.openRows()) {
                assertTrue(c.next());
                rs.close();
                IOException e = assertThrows(IOException.class, () -> c.getValue(0));
                assertTrue(e.getMessage().startsWith("Column ID:"), e.getMessage());
            }
        }
    }

    @Test
    void canOnlyBeReadOnce() throws Exception {
        exec("create table t (id int)");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select id from t")) {
            JdbcExportModel m = new JdbcExportModel(rs, 10);
            m.openRows().close();
            assertThrows(IllegalStateException.class, m::openRows);
        }
    }
}
