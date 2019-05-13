/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.metric.model.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Datamodel for the metricsproxy representation of multiple yamas checks.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class YamasArrayJsonModel {
    @JsonProperty("metrics")
    public final List<YamasJsonModel> metrics = new ArrayList<>();

    public void add(List<YamasJsonModel> results) {
        metrics.addAll(results);
    }

    public void add(YamasJsonModel result) {
        metrics.add(result);
    }

    public void add(YamasArrayJsonModel array) {
        metrics.addAll(array.metrics);
    }

    /**
     * Convenience method to serialize.
     * <p>
     * Custom floating point serializer to avoid scientifc notation
     *
     * @return Serialized json
     */
    public String serialize() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule("DoubleSerializer",
                                               new Version(1, 0, 0, "", null, null));
        module.addSerializer(Double.class, new DoubleSerializer());
        mapper.registerModule(module);

        if (metrics.size() > 0) {
            try {
                return mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }

        return "{}"; // Backwards compatability
    }

    public class DoubleSerializer extends JsonSerializer<Double> {
        @Override
        public void serialize(Double value, JsonGenerator jgen,
                              SerializerProvider provider) throws IOException, JsonProcessingException {
            DecimalFormat df = new DecimalFormat("#.####", new DecimalFormatSymbols(Locale.ENGLISH));
            df.setMaximumFractionDigits(13);
            jgen.writeNumber(df.format(value));
        }
    }
}
