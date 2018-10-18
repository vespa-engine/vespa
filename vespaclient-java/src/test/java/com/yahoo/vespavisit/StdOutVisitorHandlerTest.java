package com.yahoo.vespavisit;

import com.yahoo.documentapi.VisitorDataHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
@RunWith(Parameterized.class)
public class StdOutVisitorHandlerTest {
    private final boolean jsonOutput;

    public StdOutVisitorHandlerTest(boolean jsonOutput) {
        this.jsonOutput = jsonOutput;
    }

    @Parameterized.Parameters(name = "jsonOutput={0}")
    public static Object[] data() {
        return new Object[] { true, false};
    }

    @Test
    public void printing_ids_for_zero_documents_produces_empty_output() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StdOutVisitorHandler visitorHandler =
                new StdOutVisitorHandler(/*printIds*/true, false, false, false, false, false, 0, jsonOutput, new PrintStream(out, true));
        VisitorDataHandler dataHandler = visitorHandler.getDataHandler();
        dataHandler.onDone();
        String output = out.toString();
        assertEquals("", output.trim());
    }
}