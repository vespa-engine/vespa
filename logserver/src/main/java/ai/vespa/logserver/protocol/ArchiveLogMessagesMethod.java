// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.logserver.protocol;

import com.yahoo.jrt.DataValue;
import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Int8Value;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.logserver.LogDispatcher;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RPC method that archives incoming log messages
 *
 * @author bjorncs
 */
public class ArchiveLogMessagesMethod {

    static final String METHOD_NAME = "vespa.logserver.archiveLogMessages";

    private static final Logger log = Logger.getLogger(ArchiveLogMessagesMethod.class.getName());

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final LogDispatcher logDispatcher;
    private final Method method;

    public ArchiveLogMessagesMethod(LogDispatcher logDispatcher) {
        this.logDispatcher = logDispatcher;
        this.method = new Method(METHOD_NAME, "bix", "bix", this::log)
                .methodDesc("Archive log messages")
                .paramDesc(0, "compressionType", "Compression type (0=raw)")
                .paramDesc(1, "uncompressedSize", "Uncompressed size")
                .paramDesc(2, "logRequest", "Log request encoded with protobuf")
                .returnDesc(0, "compressionType", "Compression type (0=raw)")
                .returnDesc(1, "uncompressedSize", "Uncompressed size")
                .returnDesc(2, "logResponse", "Log response encoded with protobuf");
    }

    public Method methodDefinition() {
        return method;
    }

    private void log(Request rpcRequest) {
        rpcRequest.detach();
        executor.execute(new ArchiveLogMessagesTask(rpcRequest, logDispatcher));
    }

    private static class ArchiveLogMessagesTask implements Runnable {
        final Request rpcRequest;
        final LogDispatcher logDispatcher;

        ArchiveLogMessagesTask(Request rpcRequest, LogDispatcher logDispatcher) {
            this.rpcRequest = rpcRequest;
            this.logDispatcher = logDispatcher;
        }

        @Override
        public void run() {
            try {
                byte compressionType = rpcRequest.parameters().get(0).asInt8();
                if (compressionType != 0) {
                    rpcRequest.setError(ErrorCode.METHOD_FAILED, "Invalid compression type: " + compressionType);
                    rpcRequest.returnRequest();
                    return;
                }
                int uncompressedSize = rpcRequest.parameters().get(1).asInt32();
                byte[] logRequestPayload = rpcRequest.parameters().get(2).asData();
                if (uncompressedSize != logRequestPayload.length) {
                    rpcRequest.setError(ErrorCode.METHOD_FAILED, String.format("Invalid uncompressed size: got %d while data is of size %d ", uncompressedSize, logRequestPayload.length));
                    rpcRequest.returnRequest();
                    return;
                }
                logDispatcher.handle(ProtobufSerialization.fromLogRequest(logRequestPayload));
                logDispatcher.flush();
                rpcRequest.returnValues().add(new Int8Value((byte)0));
                byte[] responsePayload = ProtobufSerialization.toLogResponse();
                rpcRequest.returnValues().add(new Int32Value(responsePayload.length));
                rpcRequest.returnValues().add(new DataValue(responsePayload));
                rpcRequest.returnRequest();
            } catch (Exception e) {
                String errorMessage = "Failed to handle log request: " + e.getMessage();
                log.log(Level.WARNING, e, () -> errorMessage);
                rpcRequest.setError(ErrorCode.METHOD_FAILED, errorMessage);
                rpcRequest.returnRequest();
            }
        }
    }
}
