// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.config.model.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.path.Path;

import java.io.IOException;
import java.util.Optional;

/**
 * Memory statistics as reported by vespa-analyze-onnx-model.
 *
 * @author bjorncs
 */
public record OnnxMemoryStats(long vmSize, long vmRss, long mallocPeak, long mallocCurrent) {
    private static final String VM_SIZE_FIELD = "vm_size", VM_RSS_FIELD = "vm_rss",
            MALLOC_PEAK_FIELD = "malloc_peak", MALLOC_CURRENT_FIELD = "malloc_current";
    private static final ObjectMapper jsonParser = new ObjectMapper();

    /** Parse output from `vespa-analyze-onnx-model --probe-types` */
    public static OnnxMemoryStats fromJson(JsonNode json) {
        return new OnnxMemoryStats(json.get(VM_SIZE_FIELD).asLong(), json.get(VM_RSS_FIELD).asLong(),
                                   // Temporarily allow missing fields until old config model versions are gone
                                   Optional.ofNullable(json.get(MALLOC_PEAK_FIELD)).map(JsonNode::asLong).orElse(0L),
                                   Optional.ofNullable(json.get(MALLOC_CURRENT_FIELD)).map(JsonNode::asLong).orElse(0L));
    }

    /** @see #fromJson(JsonNode)  */
    public static OnnxMemoryStats fromJson(ApplicationFile file) throws IOException {
        return fromJson(jsonParser.readTree(file.createReader()));
    }

    public static Path memoryStatsFilePath(Path modelPath) {
        var fileName = modelPath.getRelative().replaceAll("[^\\w\\d\\$@_]", "_") + ".memory_stats";
        return ApplicationPackage.MODELS_GENERATED_REPLICATED_DIR.append(fileName);
    }

    public long peakMemoryUsage() { return Long.max(vmSize, Long.max(vmRss, Long.max(mallocPeak, mallocCurrent))); }

    public JsonNode toJson() {
        return jsonParser.createObjectNode().put(VM_SIZE_FIELD, vmSize).put(VM_RSS_FIELD, vmRss)
                .put(MALLOC_PEAK_FIELD, mallocPeak).put(MALLOC_CURRENT_FIELD, mallocCurrent);
    }
}

