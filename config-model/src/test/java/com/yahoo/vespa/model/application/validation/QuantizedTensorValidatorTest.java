// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QuantizedTensorValidatorTest {

    @Test
    void quantization_only_supported_for_tensor_fields() {
        var fieldSpec = quantizedField("int");
        var e = assertThrows(IllegalArgumentException.class, () -> createModelAndValidate(schema(fieldSpec)));
        assertEquals("For schema 'test', field 'f': quantization can only be set on tensor fields", e.getMessage());
    }

    @Test
    void quantization_not_supported_on_sparse_tensors() {
        var fieldSpec = quantizedField("tensor<float>(x{})");
        var e = assertThrows(IllegalArgumentException.class, () -> createModelAndValidate(schema(fieldSpec)));
        assertEquals("For schema 'test', field 'f': quantization is not supported for sparse tensors (only mixed and dense)", e.getMessage());
    }

    @Test
    void quantization_is_allowed_on_dense_tensor() {
        var fieldSpec = quantizedField("tensor<float>(x[1024])");
        assertDoesNotThrow(() -> createModelAndValidate(schema(fieldSpec)));
    }

    @Test
    void quantization_is_allowed_on_mixed_tensor() {
        var fieldSpec = quantizedField("tensor<float>(x{},y[256])");
        assertDoesNotThrow(() -> createModelAndValidate(schema(fieldSpec)));
    }

    @Test
    void geodegrees_distance_metric_is_not_supported_for_quantized_tensors() {
        var fieldSpec = quantizedField("tensor<float>(x[128])", "distance-metric: geodegrees");
        var e = assertThrows(IllegalArgumentException.class, () -> createModelAndValidate(schema(fieldSpec)));
        assertEquals("For schema 'test', field 'f': distance metric 'geodegrees' is not supported for quantized tensors", e.getMessage());
    }

    @Test
    void hamming_distance_metric_is_not_supported_for_quantized_tensors() {
        var fieldSpec = quantizedField("tensor<float>(x[128])", "distance-metric: hamming");
        var e = assertThrows(IllegalArgumentException.class, () -> createModelAndValidate(schema(fieldSpec)));
        assertEquals("For schema 'test', field 'f': distance metric 'hamming' is not supported for quantized tensors", e.getMessage());
    }

    @Test
    void expected_set_of_distance_metrics_is_allowed_for_quantized_tensor() {
        for (var m : List.of("euclidean", "angular", "innerproduct", "prenormalized-angular", "dotproduct")) {
            var fieldSpec = quantizedField("tensor<float>(x[128])", Text.format("distance-metric: %s", m));
            assertDoesNotThrow(() -> createModelAndValidate(schema(fieldSpec)));
        }
    }

    // TODO remove this test once no longer experimental
    @Test
    void quantized_field_logs_experimental_feature_deployment_warning() {
        var fieldSpec = quantizedField("tensor<float>(x[1024])");
        var logger = new TestLogger();
        assertDoesNotThrow(() -> createModelAndValidate(schema(fieldSpec), logger));
        assertEquals("WARNING: For schema 'test', field 'f': quantization support is currently EXPERIMENTAL "+
                "and should only be used for testing. It is not guaranteed that persisted, experimental " +
                "quantized tensors will be compatible across Vespa versions.\n", logger.toString());
    }

    // TODO remove this test once no longer experimental
    @Test
    void non_quantized_fields_do_not_log_deployment_warnings() {
        String fieldSpec = """
               field f type tensor<float>(x[1024]) {
                 indexing: attribute
               }
               """;
        var logger = new TestLogger();
        assertDoesNotThrow(() -> createModelAndValidate(schema(fieldSpec), logger));
        assertEquals("", logger.toString());
    }

    private static String quantizedField(String typeSpec, String extra) {
        return Text.format("""
               field f type %s {
                 indexing: attribute
                 attribute {
                   quantization {
                     bits: 4
                   }
                   %s
                 }
               }
               """, typeSpec, extra);
    }

    private static String quantizedField(String typeSpec) {
        return quantizedField(typeSpec, "");
    }

    private static String schema(String fieldSpec) {
        return Text.format("""
                schema test {
                  document test {
                    %s
                  }
                }
                """, fieldSpec);
    }

    private static class TestLogger implements DeployLogger {
        private final StringBuilder message = new StringBuilder();
        @Override
        public void log(Level level, String message) {
            this.message.append(Text.format("%s: %s\n", level.getName(), message));
        }
        public String toString() { return message.toString(); }
    }

    private static void createModelAndValidate(String schema, DeployLogger logger) {
        DeployState deployState = createDeployState(schema, logger);
        VespaModel model;
        try {
            model = new VespaModel(new NullConfigModelRegistry(), deployState);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ValidationTester.validate(new QuantizedTensorValidator(), model, deployState);
    }

    private static void createModelAndValidate(String schema) {
        createModelAndValidate(schema, null);
    }

    private static DeployState createDeployState(String schema, DeployLogger logger) {
        String servicesXml = Text.format("<services version='1.0'>\n%s\n</services>",
                new ContentClusterBuilder().getXml());
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withSchemas(List.of(schema))
                .build();
        var builder = new DeployState.Builder()
                .applicationPackage(app)
                .properties(new TestProperties().setHostedVespa(false));
        if (logger != null) {
            builder.deployLogger(logger);
        }
        return builder.build();
    }

}
