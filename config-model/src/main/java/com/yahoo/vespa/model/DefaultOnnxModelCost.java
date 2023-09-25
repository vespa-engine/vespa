// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model;

import com.yahoo.config.ModelReference;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.OnnxModelCost;
import com.yahoo.vespa.model.ml.OnnxModelProbe;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Aggregates estimated footprint of configured ONNX models.
 *
 * @author bjorncs
 */
public class DefaultOnnxModelCost implements OnnxModelCost {

    @Override
    public Calculator newCalculator(ApplicationPackage appPkg, DeployLogger logger) {
        return new CalculatorImpl(appPkg, logger);
    }

    private static class CalculatorImpl implements Calculator {
        private final DeployLogger log;
        private final ApplicationPackage appPkg;

        private final ConcurrentMap<String, Long> modelCost = new ConcurrentHashMap<>();

        private CalculatorImpl(ApplicationPackage appPkg, DeployLogger log) {
            this.appPkg = appPkg;
            this.log = log;
        }

        @Override
        public long aggregatedModelCostInBytes() {
            return modelCost.values().stream().mapToLong(Long::longValue).sum();
        }

        @Override
        public void registerModel(ApplicationFile f) {
            String path = f.getPath().getRelative();
            if (alreadyAnalyzed(path)) return;
            log.log(Level.FINE, () -> "Register model '%s'".formatted(path));
            if (f.exists() && appPkg != null) {
                var memoryStats = OnnxModelProbe.probeMemoryStats(appPkg, f.getPath()).orElse(null);
                if (memoryStats != null) {
                    log.log(Level.FINE, () -> "Register model '%s' with memory stats: %s".formatted(path, memoryStats));
                    deductJvmHeapSizeWithModelCost(f.getSize(), memoryStats, path);
                } else {
                    deductJvmHeapSizeWithModelCost(f.getSize(), path);
                }
            } else {
                deductJvmHeapSizeWithModelCost(0, path);
            }
        }

        @Override
        public void registerModel(ModelReference ref) {
            log.log(Level.FINE, () -> "Register model '%s'".formatted(ref.toString()));
            if (ref.path().isPresent()) {
                var path = Paths.get(ref.path().get().value());
                var source = path.getFileName().toString();
                if (alreadyAnalyzed(source)) return;
                deductJvmHeapSizeWithModelCost(uncheck(() -> Files.exists(path) ? Files.size(path) : 0), source);
            } else if (ref.url().isPresent()) deductJvmHeapSizeWithModelCost(URI.create(ref.url().get().value()));
            else throw new IllegalStateException(ref.toString());
        }

        private void deductJvmHeapSizeWithModelCost(URI uri) {
            if (alreadyAnalyzed(uri.toString())) return;
            if (uri.getScheme().equals("http") || uri.getScheme().equals("https")) {
                try {
                    var timeout = Duration.ofSeconds(3);
                    var httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
                    var request = HttpRequest.newBuilder(uri).timeout(timeout).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
                    var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                    var contentLength = response.headers().firstValue("Content-Length").orElse("0");
                    log.log(Level.FINE, () -> "Got content length '%s' for '%s'".formatted(contentLength, uri));
                    deductJvmHeapSizeWithModelCost(Long.parseLong(contentLength), uri.toString());
                } catch (IllegalArgumentException | InterruptedException | IOException e) {
                    log.log(Level.INFO, () -> "Failed to get model size for '%s': %s".formatted(uri, e.getMessage()), e);
                }
            }
        }

        private void deductJvmHeapSizeWithModelCost(long size, String source) {
            long fallbackModelSize = 1024*1024*1024;
            long estimatedCost = Math.max(300*1024*1024, (long) (1.4D * (size > 0 ? size : fallbackModelSize) + 100*1024*1024));
            log.log(Level.FINE, () ->
                    "Estimated %s footprint for model of size %s ('%s')".formatted(mb(estimatedCost), mb(size), source));
            modelCost.put(source, estimatedCost);
        }

        private void deductJvmHeapSizeWithModelCost(long size, OnnxModelProbe.MemoryStats stats, String source) {
            long estimatedCost = (long)(1.1D * stats.vmSize());
            log.log(Level.FINE, () ->
                    "Estimated %s footprint for model of size %s ('%s')".formatted(mb(estimatedCost), mb(size), source));
            modelCost.put(source, estimatedCost);
        }

        private boolean alreadyAnalyzed(String source) { return modelCost.containsKey(source); }

        private static String mb(long bytes) { return "%dMB".formatted(bytes / (1024*1024)); }
    }
}
