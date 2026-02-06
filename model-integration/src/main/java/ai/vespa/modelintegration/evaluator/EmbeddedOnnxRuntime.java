// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.vespa.modelintegration.utils.ModelPathOrData;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.text.Text;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;

import java.util.HashMap;
import java.util.Locale;
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
    private static final Logger log = Logger.getLogger(EmbeddedOnnxRuntime.class.getName());

    private static final OrtEnvironmentResult ortEnvironment = getOrtEnvironment();

    private final Object monitor = new Object();
    private final Map<OrtSessionId, SharedOrtSession> sessions = new HashMap<>();
    private final int gpusAvailable;

    // For test use only
    public static EmbeddedOnnxRuntime createTestInstance() { 
        return new EmbeddedOnnxRuntime(new OnnxModelsConfig.Builder().build()); 
    }

    @Inject
    public EmbeddedOnnxRuntime(OnnxModelsConfig cfg) {
        this.gpusAvailable = cfg.gpu().count();
    }

    public OnnxEvaluator evaluatorOf(byte[] model) {
        return new EmbeddedOnnxEvaluator(obtainSession(ModelPathOrData.of(model), null), ortEnvironment());
    }

    public OnnxEvaluator evaluatorOf(byte[] model, OnnxEvaluatorOptions options) {
        return new EmbeddedOnnxEvaluator(obtainSession(ModelPathOrData.of(model), overrideOptions(options)), ortEnvironment());
    }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath) {
        return new EmbeddedOnnxEvaluator(obtainSession(ModelPathOrData.of(modelPath), null), ortEnvironment());
    }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        return new EmbeddedOnnxEvaluator(obtainSession(ModelPathOrData.of(modelPath), overrideOptions(options)), ortEnvironment());
    }

    @Override
    public void deconstruct() {
        synchronized (monitor) {
            sessions.forEach((id, sharedSession) -> {
                int hash = System.identityHashCode(sharedSession.session());
                log.warning(Text.format("Closing leaked session %s (%s) with %d outstanding references:\n%s", id, hash, sharedSession.retainCount(), sharedSession.currentState()));
                try {
                    sharedSession.session().close();
                } catch (Exception e) {
                    log.log(Level.WARNING, Text.format("Failed to close session %s (%s)", id, hash), e);
                }
            });
            sessions.clear();
        }
    }

    static boolean isRuntimeAvailable() { return ortEnvironment.env() != null; }
    static boolean isRuntimeAvailable(String modelPath) {
        if (!isRuntimeAvailable()) return false;
        try {
            // Expensive way of checking if runtime is available as it incurs the cost of loading the model if successful
            ortEnvironment().createSession(modelPath, createSessionOptions(OnnxEvaluatorOptions.createDefault(), false));
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

    private static boolean isCudaError(OrtException e) {
        return switch (e.getCode()) {
            case ORT_FAIL -> e.getMessage().contains("cudaError");
            case ORT_EP_FAIL -> e.getMessage().contains("Failed to find CUDA");
            default -> false;
        };
    }

    private ReferencedOrtSession obtainSession(ModelPathOrData model, OnnxEvaluatorOptions options) {
        if (options == null) {
            options = OnnxEvaluatorOptions.createDefault();
        }
        boolean tryCuda = options.requestingGpu();
        while (true) {
            try {
                ReferencedOrtSession session = getOrCreateSession(model, options, tryCuda);
                if (tryCuda) {
                    log.log(Level.INFO, "Created session with CUDA using GPU device " + options.gpuDeviceNumber());
                }
                return session;
            } catch (OrtException e) {
                if (e.getCode() == OrtException.OrtErrorCode.ORT_NO_SUCHFILE) {
                    throw new IllegalArgumentException("No such file: " + model.path().get());
                }
                if (tryCuda && isCudaError(e) && !options.gpuDeviceRequired()) {
                    log.log(Level.INFO, "Failed to create session with CUDA using GPU device " +
                            options.gpuDeviceNumber() + ". Falling back to CPU. Reason: " + e.getMessage());
                    tryCuda = false;
                    continue; // retry with CPU
                }
                if (isCudaError(e)) {
                    throw new IllegalArgumentException("GPU device is required, but CUDA initialization failed", e);
                }
                throw new OnnxRuntimeException("ONNX Runtime exception", e);
            }
        }
    }

    private ReferencedOrtSession getOrCreateSession(ModelPathOrData model, OnnxEvaluatorOptions vespaOpts, boolean loadCuda) throws OrtException {
        var sessionId = new OrtSessionId(model.calculateHash(), vespaOpts, loadCuda);
        synchronized (monitor) {
            var existingSession = sessions.get(sessionId);
            if (existingSession != null) {
                return existingSession.newReference();
            }
            var sessionOpts = createSessionOptions(vespaOpts, loadCuda);
            var ortSession = model.path().isPresent()
                    ? ortEnvironment().createSession(model.path().get(), sessionOpts)
                    : ortEnvironment().createSession(model.data().get(), sessionOpts);
            log.fine(() -> Text.format("Created new session (%s)", System.identityHashCode(ortSession)));
            var sharedSession = new SharedOrtSession(sessionId, ortSession);
            var referencedSession = sharedSession.newReference();
            sessions.put(sessionId, sharedSession);
            sharedSession.release(); // Release "initial reference", must be released after .newReference() to keep session alive
            return referencedSession;
        }
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

    private static OrtEnvironment ortEnvironment() {
        if (ortEnvironment.env() != null) return ortEnvironment.env();
        throw throwUnchecked(ortEnvironment.failure());
    }

    private static OrtEnvironmentResult getOrtEnvironment() {
        try {
            return new OrtEnvironmentResult(OrtEnvironment.getEnvironment(), null);
        } catch (UnsatisfiedLinkError | RuntimeException | NoClassDefFoundError e) {
            log.log(Level.FINE, e, () -> "Failed to load ONNX runtime");
            return new OrtEnvironmentResult(null, e);
        }
    }

    int sessionsCached() { synchronized(monitor) { return sessions.size(); } }

    record ReferencedOrtSession(OrtSession instance, ResourceReference ref, boolean cudaLoaded) implements AutoCloseable {
        @Override public void close() { ref.close(); }
    }

    // Assumes options are never modified after being stored in `onnxSessions`
    private record OrtSessionId(long modelHash, OnnxEvaluatorOptions options, boolean loadCuda) {}

    private record OrtEnvironmentResult(OrtEnvironment env, Throwable failure) {}

    private void removeSession(OrtSessionId id) {
        synchronized (monitor) { sessions.remove(id); }
    }

    private class SharedOrtSession extends AbstractResource {
        private final OrtSessionId id;
        private final OrtSession session;

        SharedOrtSession(OrtSessionId id, OrtSession session) {
            this.id = id;
            this.session = session;
        }

        ReferencedOrtSession newReference() { return new ReferencedOrtSession(session, refer(id), id.loadCuda()); }
        OrtSession session() { return session; }

        @Override
        protected void destroy() {
            try {
                removeSession(id);
                log.fine(() -> Text.format("Closing session (%s)", System.identityHashCode(session)));
                session.close();
            } catch (OrtException e) { throw new OnnxRuntimeException(e); }
        }
    }
}
