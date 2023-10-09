// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics.docs;


import ai.vespa.metrics.Unit;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author olaa
 */
public class UnitDocumentation {

    protected static void writeUnitDocumentation(String path, Unit[] units) {
        var referenceBuilder = new StringBuilder();
        referenceBuilder.append(String.format("""
                        ---
                        # Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
                        title: "Metric Units Reference"
                        ---


                        <table class="table">
                          <thead>
                              <tr><th>Unit</th><th>Description</th></tr>
                          </thead>
                          <tbody>
                        %s    </tbody>
                        </table>
                        """, htmlRows(units)));

        try (FileWriter fileWriter = new FileWriter(path + "/unit-metrics-reference.html")) {
            fileWriter.write(referenceBuilder.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String htmlRows(Unit[] units) {
        return Stream.of(units)
                .map(unit ->
                        String.format(
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
