/* Authored by iqbserve.de */
package org.isa.jps.comp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.isa.jps.JamnPersonalServerApp;
import org.junit.jupiter.api.Test;

/**
 * 
 */
class VariousFunctionsTests {

    @Test
    void testQuotedStringRebuild() {
        String lTestString = "\"-Dmaven.repo.local=\\org.isa.jps.JamnPersonalServerApp\\workspace\\.m2ws\"";
        String[] lToken = lTestString.split(" ");

        lToken = JamnPersonalServerApp.Tool.rebuildQuotedWhitespaceStrings(lToken);
        assertEquals(lTestString, String.join(" ", lToken));

        lTestString = "copy \"C:/Program Files (x86)/Mozilla Maintenance Service/\" C:/temp /all /y /\"some strange other\" whitespaces";
        lToken = lTestString.split(" ");

        lToken = JamnPersonalServerApp.Tool.rebuildQuotedWhitespaceStrings(lToken);
        assertEquals(lTestString, String.join(" ", lToken));

    }

    @Test
    void testMissingQuoteException() {
        // missing quote test
        String lTestString = "copy \"C:/Program Files (x86)/Mozilla Maintenance Service/ C:/temp /all /y /\"some strange other\" whitespaces";
        final String[] lFailureToken = lTestString.split(" ");

        Exception lExeption = assertThrows(
                RuntimeException.class,
                () -> JamnPersonalServerApp.Tool.rebuildQuotedWhitespaceStrings(lFailureToken),
                "RuntimeException expected");

        assertTrue(lExeption.getMessage().contains("Missing start/end quote"));
    }

}
