package dev.quickexport.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SqlTextTest {

    @Test
    void leavesPlainSqlAlone() {
        assertEquals("select * from t", SqlText.stripTerminator("select * from t"));
    }

    @Test
    void trimsWhitespaceAndSemicolon() {
        assertEquals("select 1 from dual", SqlText.stripTerminator("  select 1 from dual ;  \n"));
    }

    @Test
    void stripsSlashOnItsOwnLine() {
        assertEquals("select 1 from dual", SqlText.stripTerminator("select 1 from dual\n/\n"));
        assertEquals("select 1 from dual", SqlText.stripTerminator("select 1 from dual;\r\n/"));
    }

    @Test
    void keepsSlashesInsideTheStatement() {
        assertEquals("select 10 / 2 from dual", SqlText.stripTerminator("select 10 / 2 from dual;"));
        assertEquals("select 'a;' from dual", SqlText.stripTerminator("select 'a;' from dual"));
    }

    @Test
    void keepsSemicolonInsideLiteralButRemovesTheLastOne() {
        assertEquals("select 'a;' from dual", SqlText.stripTerminator("select 'a;' from dual;;"));
    }
}
