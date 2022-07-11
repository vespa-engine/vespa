// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import org.w3c.dom.Element;
import java.util.Set;
import java.util.logging.Level;

public class DocprocOptionsBuilder {
    public static ContainerDocproc.Options build(Element spec, DeployLogger deployLogger) {
        checkForDeprecatedAttributes(spec, Set.of("maxqueuebytesize", "numnodesperclient", "preferlocalnode"), deployLogger);

        return new ContainerDocproc.Options(
                getMaxMessagesInQueue(spec),
                getTime(spec.getAttribute("maxqueuewait")),
                getFactor(spec.getAttribute("maxconcurrentfactor")),
                getFactor(spec.getAttribute("documentexpansionfactor")),
                getInt(spec.getAttribute("containercorememory")));
    }

    private static Integer getInt(String integer) {
        return integer == null || integer.trim().isEmpty()
                ? null
                : Integer.parseInt(integer);
    }

    private static Double getFactor(String factor) {
        return factor == null || factor.trim().isEmpty()
                ? null
                : Double.parseDouble(factor);
    }

    private static Integer getMaxMessagesInQueue(Element spec) {
        // get max queue size (number of messages), if set
        Integer maxMessagesInQueue = null;
        if (spec.hasAttribute("maxmessagesinqueue")) {
            maxMessagesInQueue = Integer.valueOf(spec.getAttribute("maxmessagesinqueue"));
        }
        return maxMessagesInQueue;
    }

    private static Integer getTime(String intStr) {
        if (intStr == null) {
            return null;
        }
        intStr = intStr.trim();
        if (intStr.isEmpty()) {
            return null;
        }

        return 1000 * (int)Double.parseDouble(intStr);
    }

    private static void checkForDeprecatedAttributes(Element spec, Set<String> names, DeployLogger deployLogger) {
        names.forEach(n -> {
            if (!spec.getAttribute(n).isEmpty())
                deployLogger.logApplicationPackage(Level.WARNING, "'" + n + "' is ignored, deprecated and will be removed in Vespa 9.");
        });
    }


}
