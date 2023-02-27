// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.refcount.DebugReferencesWithStack;
import com.yahoo.jdisc.refcount.References;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.throwUnchecked;

/**
 * Provides ONNX runtime environment with session management.
 *
 * @author bjorncs
 */
public class OnnxRuntime extends AbstractComponent {

    // For unit testing
    @FunctionalInterface interface OrtSessionFactory {
        OrtSession create(String path, OrtSession.SessionOptions opts) throws OrtException;
    }

    private static final Logger log = Logger.getLogger(OnnxRuntime.class.getName());

    private static final OrtEnvironmentResult ortEnvironment = getOrtEnvironment();
    private static final OrtSessionFactory defaultFactory = (path, opts) -> ortEnvironment().createSession(path, opts);

    private final Object monitor = new Object();
    private final Map<OrtSessionId, SharedOrtSession> sessions = new HashMap<>();
    private final OrtSessionFactory factory;

    @Inject public OnnxRuntime() { this(defaultFactory); }

    OnnxRuntime(OrtSessionFactory factory) { this.factory = factory; }

    public OnnxEvaluator evaluatorOf(String modelPath) {
        return new OnnxEvaluator(modelPath, null, this);
    }

    public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        return new OnnxEvaluator(modelPath, options, this);
    }

    public static OrtEnvironment ortEnvironment() {
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
            defaultFactory.create(modelPath, new OnnxEvaluatorOptions().getOptions(false));
            return true;
        } catch (OrtException e) {
            return e.getCode() == OrtException.OrtErrorCode.ORT_NO_SUCHFILE;
        } catch (UnsatisfiedLinkError | RuntimeException | NoClassDefFoundError e) {
            return false;
        }
    }

    static boolean isCudaError(OrtException e) {
        return switch (e.getCode()) {
            case ORT_FAIL -> e.getMessage().contains("cudaError");
            case ORT_EP_FAIL -> e.getMessage().contains("Failed to find CUDA");
            default -> false;
        };
    }

    ReferencedOrtSession acquireSession(String modelPath, OnnxEvaluatorOptions options, boolean loadCuda) throws OrtException {
        var sessionId = new OrtSessionId(modelPath, options, loadCuda);
        synchronized (monitor) {
            var sharedSession = sessions.get(sessionId);
            if (sharedSession != null) {
                return sharedSession.newReference();
            }
        }

        // Note: identical models loaded simultaneously will result in duplicate session instances
        var session = factory.create(modelPath, options.getOptions(loadCuda));
        log.fine(() -> "Created new session (%s)".formatted(System.identityHashCode(session)));

        var sharedSession = new SharedOrtSession(sessionId, session);
        var referencedSession = sharedSession.newReference();
        synchronized (monitor) { sessions.put(sessionId, sharedSession); }
        sharedSession.references().release(); // Release initial reference
        return referencedSession;
    }

    int sessionsCached() { synchronized(monitor) { return sessions.size(); } }

    public static class ReferencedOrtSession implements AutoCloseable {
        private final OrtSession instance;
        private final ResourceReference ref;

        public ReferencedOrtSession(OrtSession instance, ResourceReference ref) {
            this.instance = instance;
            this.ref = ref;
        }

        public OrtSession instance() { return instance; }
        @Override public void close() { ref.close(); }
    }

    // Assumes options are never modified after being stored in `onnxSessions`
    record OrtSessionId(String modelPath, OnnxEvaluatorOptions options, boolean loadCuda) {}

    record OrtEnvironmentResult(OrtEnvironment env, Throwable failure) {}

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
                synchronized (OnnxRuntime.this.monitor) { sessions.remove(id); }
                log.fine(() -> "Closing session (%s)".formatted(System.identityHashCode(session)));
                session.close();
            } catch (OrtException e) { throw new UncheckedOrtException(e);}
        }
    }
}
