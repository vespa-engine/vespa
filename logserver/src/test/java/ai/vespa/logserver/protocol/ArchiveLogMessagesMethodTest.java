// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.logserver.protocol;

import com.yahoo.jrt.DataValue;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Int8Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.Values;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.LogDispatcher;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static ai.vespa.logserver.protocol.ProtobufSerialization.fromLogResponse;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author bjorncs
 */
@SuppressWarnings("deprecation")
public class ArchiveLogMessagesMethodTest {

    private static final LogMessage MESSAGE_1 =
            LogMessage.of(Instant.EPOCH.plus(1000, ChronoUnit.DAYS), "localhost", 12, 3456, "my-service", "my-component", LogLevel.ERROR, "My error message");
    private static final LogMessage MESSAGE_2 =
            LogMessage.of(Instant.EPOCH.plus(5005, ChronoUnit.DAYS), "localhost", 12, 6543, "my-service", "my-component", Level.INFO, "My info message");

    @Test
    public void server_dispatches_log_messages_from_log_request() {
        List<LogMessage> messages = List.of(MESSAGE_1, MESSAGE_2);
        LogDispatcher logDispatcher = mock(LogDispatcher.class);
        try (RpcServer server = new RpcServer(0)) {
            server.addMethod(new ArchiveLogMessagesMethod(logDispatcher).methodDefinition());
            server.start();
            try (TestClient client = new TestClient(server.listenPort())) {
                client.logMessages(messages);
            }
        }
        verify(logDispatcher).handle(new ArrayList<>(messages));
        verify(logDispatcher).flush();
    }

    private static class TestClient implements AutoCloseable {

        private final Supervisor supervisor;
        private final Target target;

        TestClient(int logserverPort) {
            this.supervisor = new Supervisor(new Transport());
            this.target = supervisor.connect(new Spec(logserverPort));
        }

        void logMessages(List<LogMessage> messages) {
            byte[] requestPayload = ProtobufSerialization.toLogRequest(messages);
            Request request = new Request(ArchiveLogMessagesMethod.METHOD_NAME);
            request.parameters().add(new Int8Value((byte)0));
            request.parameters().add(new Int32Value(requestPayload.length));
            request.parameters().add(new DataValue(requestPayload));
            target.invokeSync(request, Duration.ofSeconds(30));
            Values returnValues = request.returnValues();
            assertEquals(3, returnValues.size());
            assertEquals(0, returnValues.get(0).asInt8());
            byte[] responsePayload = returnValues.get(2).asData();
            assertEquals(responsePayload.length, returnValues.get(1).asInt32());
            fromLogResponse(responsePayload); // 'void' return type as current response message contains no data
        }

        @Override
        public void close() {
            target.close();
            supervisor.transport().shutdown().join();
        }
    }

}