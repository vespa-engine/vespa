// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics.docs;


import ai.vespa.metrics.Unit;

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
public class UnitDocumentation {

    protected static void writeUnitDocumentation(String path, Unit[] units) {
        var referenceBuilder = new StringBuilder();
        referenceBuilder.append(String.format(Locale.ROOT, """
                        ---
                        # Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
                        title: "Metric Units Reference"
                        redirect_from:
                        - en/reference/unit-metrics-reference.html
                        ---


                        <table class="table">
                          <thead>
                              <tr><th>Unit</th><th>Description</th></tr>
                          </thead>
                          <tbody>
                        %s    </tbody>
                        </table>
                        """, htmlRows(units)));

        try (FileWriter fileWriter = new FileWriter(path + "/metric-units.html", StandardCharsets.UTF_8)) {
            fileWriter.write(referenceBuilder.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String htmlRows(Unit[] units) {
        return Stream.of(units)
                .map(unit ->
                        String.format(Locale.ROOT,
                                """
                                     <tr>
                                       <td>%s</td>
                                       <td>%s</td>
                                     </tr>
                                 """,
                                unit.fullName(),
                                unit.getDescription())
                ).collect(Collectors.joining());
    }
}
