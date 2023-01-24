// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Map;
import java.util.HashMap;

/**
 * Common code for all implementations of ApplicationPackage
 *
 * @author arnej
 */
public abstract class AbstractApplicationPackage implements ApplicationPackage {

    @Override
    public Map<String,String> legacyOverrides() {
        Map<String, String> result = new HashMap<>();
        try {
            Document services = Xml.getDocument(getServices());
            NodeList legacyNodes = services.getElementsByTagName("legacy");
            for (int i=0; i < legacyNodes.getLength(); i++) {
                var flagNodes = legacyNodes.item(i).getChildNodes();
                for (int j = 0; j < flagNodes.getLength(); ++j) {
                    var flagNode = flagNodes.item(j);
                    if (flagNode.getNodeType() == Node.ELEMENT_NODE) {
                        String key = flagNode.getNodeName();
                        String value = flagNode.getTextContent();
                        result.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            // nothing: This method does not validate that services.xml exists, or that it is valid xml.
        }
        return result;
    }

    public static boolean validSchemaFilename(String fn) {
        if (! fn.endsWith(SD_NAME_SUFFIX)) {
            return false;
        }
        int lastSlash = fn.lastIndexOf('/');
        if (lastSlash >= 0) {
            fn = fn.substring(lastSlash+1);
        }
        if (fn.startsWith(".")) {
            return false;
        }
        return true;
    }

}
