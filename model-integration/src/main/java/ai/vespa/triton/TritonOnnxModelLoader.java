package ai.vespa.triton;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Implements reference counting for loading and unloading ONNX models in NVIDIA Triton Inference Server.
 * This is to avoid loading models that are already loaded and unloading models that are still in use.
 * 
 * @author glebashnik
 */
class TritonOnnxModelLoader {
    private static final Logger logger = Logger.getLogger(TritonOnnxModelLoader.class.getName());
    
    // Lock for loading, unloading and counting operations to avoid race conditions with multiple evaluators.
    private final Object modelReferenceLock = new Object();
    private final Map<String, Integer> modelReferenceCounts = new HashMap<>();

    private final TritonOnnxClient client;
    private final boolean isExplicitControlMode;

    TritonOnnxModelLoader(TritonOnnxClient tritonClient, boolean isExplicitControlMode) {
        Objects.requireNonNull(tritonClient);
        this.client = tritonClient;
        this.isExplicitControlMode = isExplicitControlMode;
    }

    TritonOnnxClient.ModelMetadata loadModel(String modelName) {
        Objects.requireNonNull(modelName);
        TritonOnnxClient.ModelMetadata metadata;

        synchronized (modelReferenceLock) {
            var modelReferenceCount = modelReferenceCounts.getOrDefault(modelName, 0);

            if (isExplicitControlMode && modelReferenceCount == 0) {
                client.loadModel(modelName);
            }

            metadata = client.getModelMetadata(modelName);
            modelReferenceCounts.put(modelName, modelReferenceCount + 1);
        }

        return metadata;
    }

    void unloadModel(String modelName) {
        Objects.requireNonNull(modelName);
        
        synchronized (modelReferenceLock) {
            var count = modelReferenceCounts.get(modelName);

            if (count == null) {
                throw new IllegalArgumentException("No reference count for model: " + modelName);
            }

            if (count < 2) {
                modelReferenceCounts.remove(modelName);

                if (isExplicitControlMode) {
                    client.unloadModel(modelName);
                }
            } else {
                modelReferenceCounts.put(modelName, count - 1);
            }
        }
    }

    void unloadAllModels() {
        if (!isExplicitControlMode) {
            return;
        }

        synchronized (modelReferenceLock) {
            for (var modelName : modelReferenceCounts.keySet()) {
                try {
                    client.unloadModel(modelName);
                    modelReferenceCounts.remove(modelName);
                } catch (TritonOnnxClient.TritonException e) {
                    logger.warning("Failed to unload model '" + modelName + "': " + e.getMessage()); 
                }
            }
        }
    }
}
