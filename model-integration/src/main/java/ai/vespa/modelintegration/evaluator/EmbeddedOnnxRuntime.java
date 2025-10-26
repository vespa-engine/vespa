// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.vespa.modelintegration.utils.ModelPathOrData;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.refcount.DebugReferencesWithStack;
import com.yahoo.jdisc.refcount.References;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ai.onnxruntime.OrtSession.SessionOptions.ExecutionMode.PARALLEL;
import static ai.onnxruntime.OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL;
import static com.yahoo.yolean.Exceptions.throwUnchecked;

/**
 * Provides the classic embedded ONNX runtime based on the 'onnxruntime' Java library.
 *
 * @author bjorncs
 */
public class EmbeddedOnnxRuntime extends AbstractComponent implements OnnxRuntime {
    // For unit testing
    interface OrtSessionFactory {
        OrtSession create(String path, OrtSession.SessionOptions opts) throws OrtException;
        OrtSession create(byte[] data, OrtSession.SessionOptions opts) throws OrtException;
    }

    private static final Logger log = Logger.getLogger(EmbeddedOnnxRuntime.class.getName());

    private static final OrtEnvironmentResult ortEnvironment = getOrtEnvironment();
    private static final OrtSessionFactory defaultFactory = new OrtSessionFactory() {
        @Override public OrtSession create(String path, OrtSession.SessionOptions opts) throws OrtException {
            return ortEnvironment().createSession(path, opts);
        }
        @Override public OrtSession create(byte[] data, OrtSession.SessionOptions opts) throws OrtException {
            return ortEnvironment().createSession(data, opts);
        }
    };

    private final Object monitor = new Object();
    private final Map<OrtSessionId, SharedOrtSession> sessions = new HashMap<>();
    private final OrtSessionFactory factory;
    private final int gpusAvailable;

    // For test use only
    public EmbeddedOnnxRuntime() { this(defaultFactory, new OnnxModelsConfig.Builder().build()); }

    @Inject
    public EmbeddedOnnxRuntime(OnnxModelsConfig cfg) { this(defaultFactory, cfg); }

    EmbeddedOnnxRuntime(OrtSessionFactory factory, OnnxModelsConfig cfg) {
        this.factory = factory;
        this.gpusAvailable = cfg.gpu().count();
    }

    public OnnxEvaluator evaluatorOf(byte[] model) {
        return new EmbeddedOnnxEvaluator(model, null, this);
    }

    public OnnxEvaluator evaluatorOf(byte[] model, OnnxEvaluatorOptions options) {
        return new EmbeddedOnnxEvaluator(model, overrideOptions(options), this);
    }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath) {
        return new EmbeddedOnnxEvaluator(modelPath, null, this);
    }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        return new EmbeddedOnnxEvaluator(modelPath, overrideOptions(options), this);
    }

    static OrtEnvironment ortEnvironment() {
        if (ortEnvironment.env() != null) return ortEnvironment.env();
        throw throwUnchecked(ortEnvironment.failure());
    }

    @Override
    public void deconstruct() {
        synchronized (monitor) {
            sessions.forEach((id, sharedSession) -> {
                int hash = System.identityHashCode(sharedSession.session());
                var refs = sharedSession.references();
                log.warning("Closing leaked session %s (%s) with %d outstanding references:\n%s"
                        .formatted(id, hash, refs.referenceCount(), refs.currentState()));
                try {
                    sharedSession.session().close();
                } catch (Exception e) {
                    log.log(Level.WARNING, "Failed to close session %s (%s)".formatted(id, hash), e);
                }
            });
            sessions.clear();
        }
    }

    private static OrtEnvironmentResult getOrtEnvironment() {
        try {
            return new OrtEnvironmentResult(OrtEnvironment.getEnvironment(), null);
        } catch (UnsatisfiedLinkError | RuntimeException | NoClassDefFoundError e) {
            log.log(Level.FINE, e, () -> "Failed to load ONNX runtime");
            return new OrtEnvironmentResult(null, e);
        }
    }

    public static boolean isRuntimeAvailable() { return ortEnvironment.env() != null; }
    public static boolean isRuntimeAvailable(String modelPath) {
        if (!isRuntimeAvailable()) return false;
        try {
            // Expensive way of checking if runtime is available as it incurs the cost of loading the model if successful
            defaultFactory.create(modelPath, createSessionOptions(OnnxEvaluatorOptions.createDefault(), false));
            return true;
        } catch (OrtException e) {
            return e.getCode() == OrtException.OrtErrorCode.ORT_NO_SUCHFILE;
        } catch (UnsatisfiedLinkError | RuntimeException | NoClassDefFoundError e) {
            return false;
        }
    }

    private static OrtSession.SessionOptions createSessionOptions(OnnxEvaluatorOptions vespaOpts, boolean loadCuda) throws OrtException {
        var sessionOpts = new OrtSession.SessionOptions();
        sessionOpts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        var execMode = vespaOpts.executionMode() == OnnxEvaluatorOptions.ExecutionMode.PARALLEL ? PARALLEL : SEQUENTIAL;
        sessionOpts.setExecutionMode(execMode);
        sessionOpts.setInterOpNumThreads(execMode == PARALLEL ? vespaOpts.interOpThreads() : 1);
        sessionOpts.setIntraOpNumThreads(vespaOpts.intraOpThreads());
        sessionOpts.setCPUArenaAllocator(false);
        if (loadCuda) sessionOpts.addCUDA(vespaOpts.gpuDeviceNumber());
        return sessionOpts;
    }

    static boolean isCudaError(OrtException e) {
        return switch (e.getCode()) {
            case ORT_FAIL -> e.getMessage().contains("cudaError");
            case ORT_EP_FAIL -> e.getMessage().contains("Failed to find CUDA");
            default -> false;
        };
    }

    ReferencedOrtSession acquireSession(ModelPathOrData model, OnnxEvaluatorOptions vespaOpts, boolean loadCuda) throws OrtException {
        var sessionId = new OrtSessionId(model.calculateHash(), vespaOpts, loadCuda);
        synchronized (monitor) {
            var sharedSession = sessions.get(sessionId);
            if (sharedSession != null) {
                return sharedSession.newReference();
            }
        }

        var sessionOpts = createSessionOptions(vespaOpts, loadCuda);
        // Note: identical models loaded simultaneously will result in duplicate session instances
        var session = model.path().isPresent() ? factory.create(model.path().get(), sessionOpts) : factory.create(model.data().get(), sessionOpts);
        log.fine(() -> "Created new session (%s)".formatted(System.identityHashCode(session)));

        var sharedSession = new SharedOrtSession(sessionId, session);
        var referencedSession = sharedSession.newReference();
        synchronized (monitor) { sessions.put(sessionId, sharedSession); }
        sharedSession.references().release(); // Release initial reference
        return referencedSession;
    }

    private OnnxEvaluatorOptions overrideOptions(OnnxEvaluatorOptions vespaOpts) {
        // Set GPU device required if GPU requested and GPUs are available on system
        if (gpusAvailable > 0 && vespaOpts.requestingGpu() && !vespaOpts.gpuDeviceRequired()) {
            // Create a new instance with updated gpuDeviceRequired value using the builder
            return new OnnxEvaluatorOptions.Builder(vespaOpts)
                    .setGpuDevice(vespaOpts.gpuDeviceNumber(), true)
                    .build();
        }
        return vespaOpts;
    }

    int sessionsCached() { synchronized(monitor) { return sessions.size(); } }

    static class ReferencedOrtSession implements AutoCloseable {
        private final OrtSession instance;
        private final ResourceReference ref;

        ReferencedOrtSession(OrtSession instance, ResourceReference ref) {
            this.instance = instance;
            this.ref = ref;
        }

        OrtSession instance() { return instance; }
        @Override public void close() { ref.close(); }
    }

    // Assumes options are never modified after being stored in `onnxSessions`
    private record OrtSessionId(long modelHash, OnnxEvaluatorOptions options, boolean loadCuda) {}

    private record OrtEnvironmentResult(OrtEnvironment env, Throwable failure) {}

    private class SharedOrtSession {
        private final OrtSessionId id;
        private final OrtSession session;
        private final References refs = new DebugReferencesWithStack(this::close);

        SharedOrtSession(OrtSessionId id, OrtSession session) {
            this.id = id;
            this.session = session;
        }

        ReferencedOrtSession newReference() { return new ReferencedOrtSession(session, refs.refer(id)); }
        References references() { return refs; }
        OrtSession session() { return session; }

        void close() {
            try {
                synchronized (monitor) { sessions.remove(id); }
                log.fine(() -> "Closing session (%s)".formatted(System.identityHashCode(session)));
                session.close();
            } catch (OrtException e) { throw new UncheckedOrtException(e);}
        }
    }
}
