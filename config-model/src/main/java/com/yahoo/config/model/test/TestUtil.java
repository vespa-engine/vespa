// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.test;

import com.yahoo.collections.CollectionUtil;
import com.yahoo.config.model.builder.xml.XmlHelper;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Ulf Lilleengen
 * @author gjoranv
 */
public class TestUtil {
    /**
     * @param xmlLines XML with " replaced with '
     */
    public static Element parse(String... xmlLines) {
        List<String> lines = new ArrayList<>();
        lines.add("<?xml version='1.0' encoding='utf-8' ?>");
        lines.addAll(Arrays.asList(xmlLines));

        try {
            return XmlHelper.getDocument(new StringReader(CollectionUtil.mkString(lines, "\n").replace("'", "\""))).getDocumentElement();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String joinLines(CharSequence... lines) {
        return String.join("\n", lines);
    }

    private static InputSource inputSource(String str) {
        return new InputSource(new StringReader(str));
    }
}
