// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespafeeder;

import com.yahoo.clientmetrics.RouteMetricSet;
import com.yahoo.concurrent.ManualTimer;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.messagebus.EmptyReply;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BenchmarkProgressPrinterTest {

    @Test
    void testSimple() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ManualTimer timer = new ManualTimer();
        BenchmarkProgressPrinter printer = new BenchmarkProgressPrinter(timer, new PrintStream(output));
        RouteMetricSet metrics = new RouteMetricSet("foobar", printer);

        {
            EmptyReply reply = new EmptyReply();
            reply.setMessage(PutDocumentMessage.createEmpty().setTimeReceived(1));
            metrics.addReply(reply);
        }

        timer.set(1200);

        {
            EmptyReply reply = new EmptyReply();
            reply.setMessage(PutDocumentMessage.createEmpty().setTimeReceived(2));
            metrics.addReply(reply);
        }

        {
            EmptyReply reply = new EmptyReply();
            reply.setMessage(UpdateDocumentMessage.createEmpty().setTimeReceived(3));
            metrics.addReply(reply);
        }

        timer.set(2400);

        {
            EmptyReply reply = new EmptyReply();
            reply.setMessage(UpdateDocumentMessage.createEmpty().setTimeReceived(4));
            reply.addError(new com.yahoo.messagebus.Error(32, "foo"));
            metrics.addReply(reply);
        }

        timer.set(62000);

        {
            EmptyReply reply = new EmptyReply();
            reply.setMessage(UpdateDocumentMessage.createEmpty().setTimeReceived(5));
            reply.addError(new com.yahoo.messagebus.Error(64, "bar"));
            metrics.addReply(reply);
        }

        metrics.done();

        String val = output.toString().split("\n")[1];

        String correctPattern = "62000, \\d+, \\d+, \\d+, \\d+, \\d+$";
        assertTrue(val.matches(correctPattern), "Value '" + val + "' does not match pattern '" + correctPattern + "'");
    }

}
