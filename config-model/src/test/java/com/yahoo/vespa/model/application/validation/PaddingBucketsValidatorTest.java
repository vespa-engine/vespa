// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import com.yahoo.config.FileReference;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.deploy.TestDeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author ingridhabrekke
 * @author glebashnik
 */
class PaddingBucketsValidatorTest {

    private static final List<Integer> BUCKETS = List.of(16, 64);

    @Test
    void paddingHasNoEffectWithoutBatching() {
        assertEquals(Optional.of("the batching max-size is not above 1"), reason(config(BUCKETS, 1, 0)));
    }

    @Test
    void paddingHasNoEffectOnCpu() {
        assertEquals(Optional.of("onnx-gpu-device is -1, which means CPU"), reason(config(BUCKETS, 8, -1)));
    }

    @Test
    void paddingMayHaveEffectOnAGpu() {
        assertEquals(Optional.empty(), reason(config(BUCKETS, 8, 0)));
        assertEquals(Optional.empty(), reason(config(BUCKETS, 8, 2)));
    }

    @Test
    void paddingIsDecidedByTheModelConfigOverrideWhenThereIsOne() {
        var config = new OnnxEvaluatorConfig.Builder().modelConfigOverride(Optional.of(new FileReference("config.pbtxt")));
        config.batching.paddingBuckets(BUCKETS);
        assertEquals(Optional.empty(), reason(config.build()));
    }

    @Test
    void nothingIsReportedWithoutBuckets() {
        assertEquals(Optional.empty(), reason(config(List.of(), 1, -1)));
    }

    @Test
    void warnsForEmbeddersWhosePaddingHasNoEffect() throws IOException, SAXException {
        var logger = new DeployLoggerStub();
        validate(logger, embedder("padded", "<batching max-size='8' max-delay='1ms' padding-buckets='16 64'/>"),
                         embedder("unbatched", "<batching max-size='1' max-delay='1ms' padding-buckets='16 64'/>"),
                         embedder("on-cpu", "<onnx-gpu-device>-1</onnx-gpu-device><batching max-size='8' max-delay='1ms' padding-buckets='16'/>"),
                         embedder("unpadded", "<batching max-size='1' max-delay='1ms'/>"));
        var warnings = logger.entries.stream().filter(entry -> entry.message.contains("padding-buckets")).collect(Collectors.toSet());
        assertEquals(Set.of(new DeployLoggerStub.LogEntry(Level.WARNING,
                                     "For component 'unbatched': padding-buckets has no effect since the batching max-size " +
                                     "is not above 1. Padding requires a batching max-size above 1 on a GPU"),
                             new DeployLoggerStub.LogEntry(Level.WARNING,
                                     "For component 'on-cpu': padding-buckets has no effect since onnx-gpu-device is -1, " +
                                     "which means CPU. Padding requires a batching max-size above 1 on a GPU")),
                     warnings);
    }

    private static Optional<String> reason(OnnxEvaluatorConfig config) {
        return PaddingBucketsValidator.reasonPaddingHasNoEffect(config);
    }

    private static OnnxEvaluatorConfig config(List<Integer> buckets, int batchingMaxSize, int gpuDevice) {
        var config = new OnnxEvaluatorConfig.Builder().gpuDevice(gpuDevice);
        config.batching.maxSize(batchingMaxSize).paddingBuckets(buckets);
        return config.build();
    }

    private static String embedder(String id, String children) {
        return joinLines("<component id='" + id + "' type='hugging-face-embedder'>",
                         "  <transformer-model url='https://my/url/model.onnx'/>",
                         "  <tokenizer-model url='https://my/url/tokenizer.json'/>",
                         children,
                         "</component>");
    }

    private static void validate(DeployLoggerStub logger, String... components) throws IOException, SAXException {
        var services = joinLines("<services version='1.0'>",
                                 "  <container version='1.0'>",
                                 String.join("\n", components),
                                 "  </container>",
                                 "</services>");
        var state = TestDeployState.createBuilder()
                                   .applicationPackage(new MockApplicationPackage.Builder().withServices(services).build())
                                   .properties(new TestProperties())
                                   .deployLogger(logger)
                                   .build();
        new PaddingBucketsValidator().validate(Validation.createContext(new VespaModel(new NullConfigModelRegistry(), state), state));
    }

}
