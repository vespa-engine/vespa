// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import org.w3c.dom.Element;

/**
 * Extracted from DomDocProcClusterBuilder
 */
public class DocprocOptionsBuilder {
    public static ContainerDocproc.Options build(Element spec) {
        return new ContainerDocproc.Options(
                getCompression(spec),
                getMaxMessagesInQueue(spec),
                getSizeInMegabytes(spec.getAttribute("maxqueuebytesize")),
                getTime(spec.getAttribute("maxqueuewait")),
                getFactor(spec.getAttribute("maxconcurrentfactor")),
                getFactor(spec.getAttribute("documentexpansionfactor")),
                getInt(spec.getAttribute("containercorememory")));
    }

    private static Integer getInt(String integer) {
        return integer == null || integer.trim().isEmpty() ?
                null:
                Integer.parseInt(integer);
    }

    private static boolean getCompression(Element spec) {
        return (spec.hasAttribute("compressdocuments") && spec.getAttribute("compressdocuments").equals("true"));
    }

    private static Double getFactor(String factor) {
        return factor == null || factor.trim().isEmpty() ?
                null :
                Double.parseDouble(factor);
    }


    private static Integer getMaxMessagesInQueue(Element spec) {
        // get max queue size (number of messages), if set
        Integer maxMessagesInQueue = null;
        if (spec.hasAttribute("maxmessagesinqueue")) {
            maxMessagesInQueue = Integer.valueOf(spec.getAttribute("maxmessagesinqueue"));
        }
        return maxMessagesInQueue;
    }

    private static Integer getSizeInMegabytes(String size) {
        if (size == null) {
            return null;
        }
        size = size.trim();
        if (size.isEmpty()) {
            return null;
        }

        Integer megabyteSize;
        if (size.endsWith("m")) {
            size = size.substring(0, size.length() - 1);
            megabyteSize = Integer.parseInt(size);
        } else if (size.endsWith("g")) {
            size = size.substring(0, size.length() - 1);
            megabyteSize = Integer.parseInt(size) * 1024;
        } else {
            throw new IllegalArgumentException("Heap sizes for docproc must be set to Xm or Xg, where X is an integer specifying megabytes or gigabytes, respectively.");
        }
        return megabyteSize;
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
}
