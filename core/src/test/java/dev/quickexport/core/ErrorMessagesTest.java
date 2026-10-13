package dev.quickexport.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

class ErrorMessagesTest {

    @Test
    void permissionProblemNamesTheFile() {
        String text = ErrorMessages.describe(new AccessDeniedException("C:\\data\\out.csv"));
        assertTrue(text.contains("permission") && text.contains("C:\\data\\out.csv"), text);
    }

    @Test
    void fileHeldByAnotherProgramSaysSoAndNamesTheFile() {
        FileSystemException locked = new FileSystemException("out.xlsx", null,
                "The process cannot access the file because it is being used by another process");
        String text = ErrorMessages.describe(locked);
        assertTrue(text.contains("open in another program") && text.contains("out.xlsx"), text);
    }

    @Test
    void otherFileSystemProblemsIncludeTheReason() {
        String text = ErrorMessages.describe(new FileSystemException("out.csv", null, "No space left on device"));
        assertEquals("Could not write the file:\nout.csv\nNo space left on device", text);
    }

    @Test
    void sqlErrorIsFoundInsideTheCauseChain() {
        IOException wrapped = new IOException("wrapper", new SQLException("ORA-00942: table or view does not exist\n"));
        assertEquals("The query failed:\nORA-00942: table or view does not exist", ErrorMessages.describe(wrapped));
    }

    @Test
    void plainIoErrorIsLabelledAsExportFailure() {
        assertEquals("The export failed:\nboom", ErrorMessages.describe(new IOException("boom")));
    }

    @Test
    void unexpectedErrorsKeepTheirTypeAndSurviveANullMessage() {
        assertEquals("Unexpected error:\nIllegalStateException: bad", ErrorMessages.describe(new IllegalStateException("bad")));
        assertEquals("Unexpected error:\nNullPointerException: NullPointerException", ErrorMessages.describe(new NullPointerException()));
    }
}
