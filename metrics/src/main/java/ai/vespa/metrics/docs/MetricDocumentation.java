// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics.docs;

import ai.vespa.metrics.VespaMetrics;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author olaa
 */
public class MetricDocumentation {

    protected static void writeMetricDocumentation(String path, VespaMetrics[] metrics, String metricType) {
        var referenceBuilder = new StringBuilder();
        referenceBuilder.append(String.format(Locale.ROOT, """
                        ---
                        # Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
                        title: "%s Metrics"
                        redirect_from:
                        - en/reference/%s-metrics-reference.html
                        ---

                        <table class="table">
                          <thead>
                              <tr><th>Name</th><th>Unit</th><th>Description</th></tr>
                          </thead>
                          <tbody>
                        %s  </tbody>
                        </table>
                        """, metricType, metricType.toLowerCase(Locale.ROOT), htmlRows(metrics)));

        try (FileWriter fileWriter = new FileWriter(path + "/" + metricType.toLowerCase(Locale.ROOT) + ".html", StandardCharsets.UTF_8)) {
            fileWriter.write(referenceBuilder.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String htmlRows(VespaMetrics[] metrics) {
        return Stream.of(metrics)
                .map(metric ->
                        String.format(Locale.ROOT,
                                """
                                     <tr>
                                       <td><p id="%s">%s</p></td>
                                       <td>%s</td>
                                       <td>%s</td>
                                     </tr>
                                 """,
                                metric.baseName().replaceAll("\\.", "_"),
                                metric.baseName(),
                                metric.unit().toString().toLowerCase(Locale.ROOT),
                                metric.description())
                ).collect(Collectors.joining());
    }
}
