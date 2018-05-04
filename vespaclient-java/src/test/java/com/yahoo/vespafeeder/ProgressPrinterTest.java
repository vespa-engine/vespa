// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespafeeder;

import com.yahoo.clientmetrics.RouteMetricSet;
import com.yahoo.concurrent.Timer;
import com.yahoo.documentapi.messagebus.protocol.DocumentIgnoredReply;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.messagebus.EmptyReply;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;

public class ProgressPrinterTest {

    class DummyTimer implements Timer {
        long ms;

        public long milliTime() { return ms; }
    }

    @Test
    public void testSimple() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DummyTimer timer = new DummyTimer();
        timer.ms = 0;
        ProgressPrinter printer = new ProgressPrinter(timer, new PrintStream(output));
        RouteMetricSet metrics = new RouteMetricSet("foobar", printer);

        {
            EmptyReply reply = new EmptyReply();
            reply.setMessage(PutDocumentMessage.createEmpty().setTimeReceived(1));
            metrics.addReply(reply);
        }

        timer.ms = 1200;

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

        timer.ms = 2400;

        {
            DocumentIgnoredReply reply = new DocumentIgnoredReply();
            reply.setMessage(PutDocumentMessage.createEmpty().setTimeReceived(0));
            metrics.addReply(reply);
        }

        {
            EmptyReply reply = new EmptyReply();
            reply.setMessage(UpdateDocumentMessage.createEmpty().setTimeReceived(5));
            reply.addError(new com.yahoo.messagebus.Error(32, "foo"));
            metrics.addReply(reply);
        }

        timer.ms = 62000;

        {
            EmptyReply reply = new EmptyReply();
            reply.setMessage(UpdateDocumentMessage.createEmpty().setTimeReceived(6));
            reply.addError(new com.yahoo.messagebus.Error(64, "bar"));
            metrics.addReply(reply);
        }

        String val = output.toString().replaceAll("latency\\(min, max, avg\\): .*", "latency(min, max, avg): 0, 0, 0");

        String correct =
                "\rSuccessfully sent 2 messages so far" +
                "\rSuccessfully sent 3 messages so far" +
                "\n\n" +
                "Messages sent to vespa (route foobar) :\n" +
                "---------------------------------------\n" +
                "PutDocument:\tok: 2 msgs/sec: 0.03 failed: 0 ignored: 1 latency(min, max, avg): 0, 0, 0\n" +
                "UpdateDocument:\tok: 1 msgs/sec: 0.02 failed: 2 ignored: 0 latency(min, max, avg): 0, 0, 0\n";

        assertEquals(correct, val);
    }

}
