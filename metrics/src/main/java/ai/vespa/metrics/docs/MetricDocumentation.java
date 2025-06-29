// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics.docs;

import ai.vespa.metrics.VespaMetrics;
import com.yahoo.text.Utf8;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author olaa
 */
public class MetricDocumentation {

    protected static void writeMetricDocumentation(String path, VespaMetrics[] metrics, String metricType) {
        var referenceBuilder = new StringBuilder();
        referenceBuilder.append(String.format("""
                        ---
                        # Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
                        title: "%s Metrics"
                        ---

                        <table class="table">
                          <thead>
                              <tr><th>Name</th><th>Unit</th><th>Description</th></tr>
                          </thead>
                          <tbody>
                        %s  </tbody>
                        </table>
                        """, metricType, htmlRows(metrics)));

        try (FileWriter fileWriter = Utf8.createWriter(path + "/" + metricType.toLowerCase() + "-metrics-reference.html")) {
            fileWriter.write(referenceBuilder.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String htmlRows(VespaMetrics[] metrics) {
        return Stream.of(metrics)
                .map(metric ->
                        String.format(
                                """
                                     <tr>
                                       <td><p id="%s">%s</p></td>
                                       <td>%s</td>
                                       <td>%s</td>
                                     </tr>
                                 """,
                                metric.baseName().replaceAll("\\.", "_"),
                                metric.baseName(),
                                metric.unit().toString().toLowerCase(),
                                metric.description())
                ).collect(Collectors.joining());
    }
}
