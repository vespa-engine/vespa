// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * @author smorgrav
 * @author gjoranv
 */
public class JacksonUtil {

    private static final DecimalFormat decimalFormat = createDecimalFormat();
    private static DecimalFormat createDecimalFormat() {
        DecimalFormat df = new DecimalFormat("#.####", new DecimalFormatSymbols(Locale.ENGLISH));
        df.setMaximumFractionDigits(13);
        return df;
    }
    /**
     * Returns an object mapper with a custom floating point serializer to avoid scientific notation
     */
    public static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule("DoubleSerializer",
                                               new Version(1, 0, 0, "", null, null));
        module.addSerializer(Double.class, new DoubleSerializer());
        mapper.registerModule(module);
        return mapper;
    }
    /**
     * Returns an object mapper with a custom floating point serializer to avoid scientific notation
     */
    public static void writeDouble(JsonGenerator jgen, Double value) throws IOException {
        jgen.writeNumber(decimalFormat.format(value));
    }

    public static class DoubleSerializer extends JsonSerializer<Double> {
        @Override
        public void serialize(Double value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            writeDouble(jgen, value);
        }
    }

}
