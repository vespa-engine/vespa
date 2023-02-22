// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.jdisc.ResourceReference;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Caches instances of {@link OnnxEvaluator}.
 *
 * @author bjorncs
 */
public class OnnxEvaluatorCache {

    // For mocking OnnxEvaluator in tests
    @FunctionalInterface interface OnnxEvaluatorFactory { OnnxEvaluator create(String path, OnnxEvaluatorOptions opts); }

    private final Object monitor = new Object();
    private final Map<Id, SharedEvaluator> cache = new HashMap<>();
    private final OnnxEvaluatorFactory factory;

    @Inject public OnnxEvaluatorCache() { this(OnnxEvaluator::new); }

    OnnxEvaluatorCache(OnnxEvaluatorFactory factory) { this.factory = factory; }

    public ReferencedEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        synchronized (monitor) {
            var id = new Id(modelPath, options);
            var sharedInstance = cache.get(id);
            if (sharedInstance == null) {
                return newInstance(id);
            } else {
                ResourceReference reference;
                try {
                    // refer() may throw if last reference was just released, but instance has not yet been removed from cache
                    reference = sharedInstance.refer(id);
                } catch (IllegalStateException e) {
                    return newInstance(id);
                }
                return new ReferencedEvaluator(sharedInstance, reference);
            }
        }
    }

    int size() { return cache.size(); }

    private ReferencedEvaluator newInstance(Id id) {
        var evaluator = new SharedEvaluator(id, factory.create(id.modelPath, id.options));
        cache.put(id, evaluator);
        var referenced = new ReferencedEvaluator(evaluator, evaluator.refer(id));
        // Release "main" reference to ensure that evaluator is destroyed when last external reference is released
        evaluator.release();
        return referenced;
    }

    // We assume options are never modified after being passed to cache
    record Id(String modelPath, OnnxEvaluatorOptions options) {}

    public class ReferencedEvaluator extends ReferencedResource<SharedEvaluator> {
        ReferencedEvaluator(SharedEvaluator resource, ResourceReference reference) { super(resource, reference); }

        public OnnxEvaluator evaluator() { return getResource().instance(); }
    }

    public class SharedEvaluator extends AbstractResource {
        private final Id id;
        private final OnnxEvaluator instance;

        private SharedEvaluator(Id id, OnnxEvaluator instance) {
            this.id = id;
            this.instance = instance;
        }

        public OnnxEvaluator instance() { return instance; }

        @Override
        protected void destroy() {
            synchronized (OnnxEvaluatorCache.this) { cache.remove(id); }
            instance.close();
        }
    }

}
