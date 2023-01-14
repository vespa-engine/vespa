// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespavisit;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.documentapi.AckToken;
import com.yahoo.documentapi.VisitorControlSession;
import com.yahoo.documentapi.VisitorDataHandler;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * @author bjorncs
 */
public class StdOutVisitorHandlerTest {
    private boolean jsonOutput;

    public void initStdOutVisitorHandlerTest(boolean jsonOutput) {
        this.jsonOutput = jsonOutput;
    }

    public static Object[] data() {
        return new Object[]{true, false};
    }

    @MethodSource("data")
    @ParameterizedTest(name = "jsonOutput={0}")
    void printing_ids_for_zero_documents_produces_empty_output(boolean jsonOutput) {
        initStdOutVisitorHandlerTest(jsonOutput);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StdOutVisitorHandler visitorHandler =
                new StdOutVisitorHandler(/*printIds*/true, false, false, false, false, false, 0, jsonOutput, false, false, new PrintStream(out, true));
        VisitorDataHandler dataHandler = visitorHandler.getDataHandler();
        dataHandler.onDone();
        String output = out.toString();
        assertEquals("", output.trim());
    }

    @MethodSource("data")
    @ParameterizedTest(name = "jsonOutput={0}")
    void printing_zero_documents_produces_empty_output(boolean jsonOutput) {
        initStdOutVisitorHandlerTest(jsonOutput);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StdOutVisitorHandler visitorHandler =
                new StdOutVisitorHandler(/*printIds*/false, false, false, false, false, false, 0, jsonOutput, false, false, new PrintStream(out, true));
        VisitorDataHandler dataHandler = visitorHandler.getDataHandler();
        dataHandler.onDone();
        String expectedOutput = jsonOutput ? "[]" : "";
        String output = out.toString().trim();
        assertEquals(expectedOutput, output);
    }

    void do_test_json_tensor_fields_rendering(boolean tensorShortForm, boolean tensorDirectValues, String expectedOutput) {
        var docType = new DocumentType("foo");
        docType.addField("bar", TensorDataType.getTensor(TensorType.fromSpec("tensor(x[3])")));
        var doc = new Document(docType, "id:baz:foo::tensor-stuff");
        doc.setFieldValue("bar", new TensorFieldValue(Tensor.from("tensor(x[3]):[1,2,3]")));
        var putMsg = new PutDocumentMessage(new DocumentPut(doc));

        var out = new ByteArrayOutputStream();
        var visitorHandler = new StdOutVisitorHandler(/*printIds*/false, false, false, false, false, false,
                                                      0, true, tensorShortForm, tensorDirectValues, new PrintStream(out, true));
        var dataHandler = visitorHandler.getDataHandler();
        var controlSession = mock(VisitorControlSession.class);
        var ackToken = mock(AckToken.class);
        dataHandler.setSession(controlSession);
        dataHandler.onMessage(putMsg, ackToken);
        dataHandler.onDone();

        String output = out.toString().trim();
        assertEquals(expectedOutput, output);
    }

    @Test
    void json_tensor_fields_can_be_output_in_long_form() {
        var expectedOutput = """
        [
        {"id":"id:baz:foo::tensor-stuff","fields":{"bar":{"type":"tensor(x[3])","cells":[{"address":{"x":"0"},"value":1.0},{"address":{"x":"1"},"value":2.0},{"address":{"x":"2"},"value":3.0}]}}}]""";
        do_test_json_tensor_fields_rendering(false, false, expectedOutput);
    }

    @Test
    void json_tensor_fields_can_be_output_in_short_form() {
        var expectedOutput = """
        [
        {"id":"id:baz:foo::tensor-stuff","fields":{"bar":{"type":"tensor(x[3])","values":[1.0,2.0,3.0]}}}]""";
        do_test_json_tensor_fields_rendering(true, false, expectedOutput);
    }

}
