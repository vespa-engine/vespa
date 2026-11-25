// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import com.yahoo.language.process.TimeoutException;
import inference.GRPCInferenceServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractStub;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * What the clients of Triton's gRPC API share: creating and closing a channel, and translating failures.
 *
 * @author bjorncs
 * @author glebashnik
 */
final class TritonGrpc {

    private static final int MAX_INBOUND_MESSAGE_SIZE_MIB = 128;

    private TritonGrpc() { }

    @FunctionalInterface
    interface GrpcInvocation<T, S extends AbstractBlockingStub<S>> {
        T apply(S stub) throws StatusException;
    }

    /** Returns a stub of a new channel to the Triton of the config, which {@link #close} closes. */
    static GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingV2Stub newStub(TritonConfig config) {
        var channel = ManagedChannelBuilder.forTarget(config.target())
                .usePlaintext()
                .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE_MIB * 1024 * 1024)
                .build();
        return GRPCInferenceServiceGrpc.newBlockingV2Stub(channel);
    }

    /** Closes the channel of the stub. */
    static void close(GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingV2Stub stub) {
        var channel = (ManagedChannel) invoke(stub, AbstractStub::getChannel, "Failed to get channel");
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, SECONDS)) throw new IllegalStateException("Failed to close channel");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TritonException("Failed to close channel", e);
        } finally {
            channel.shutdownNow();
        }
    }

    /**
     * Invokes the stub, translating gRPC failures.
     *
     * @throws TimeoutException if the stub's deadline passes
     * @throws TritonException on any other failure
     */
    static <T, S extends AbstractBlockingStub<S>> T invoke(S stub, GrpcInvocation<T, S> invocation, String errorMessage) {
        try {
            return invocation.apply(stub);
        } catch (StatusException e) {
            throw translateFailure(e.getStatus(), errorMessage, e);
        } catch (StatusRuntimeException e) {
            throw translateFailure(e.getStatus(), errorMessage, e);
        }
    }

    private static RuntimeException translateFailure(Status status, String errorMessage, Throwable cause) {
        if (status.getCode() == Status.Code.DEADLINE_EXCEEDED)
            return new TimeoutException(errorMessage + ": deadline exceeded", cause);
        return new TritonException(errorMessage, cause);
    }

}
