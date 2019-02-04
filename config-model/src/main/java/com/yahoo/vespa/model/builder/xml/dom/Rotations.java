// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.provision.RotationName;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Read rotations from the <rotations/> element in services.xml.
 *
 * @author mpolden
 */
public class Rotations {

    /*
     * Rotation names must be:
     * - lowercase
     * - alphanumeric
     * - begin with a character
     * - have a length between 1 and 12
     */
    private static final Pattern pattern = Pattern.compile("^[a-z][a-z0-9-]{0,11}$");

    private Rotations() {}

    /** Set the rotations the given cluster should be member of */
    public static Set<RotationName> from(Element rotationsElement) {
        Set<RotationName> rotations = new TreeSet<>();
        List<Element> children = XML.getChildren(rotationsElement, "rotation");
        for (Element el : children) {
            String name = el.getAttribute("id");
            if (name == null || !pattern.matcher(name).find()) {
                throw new IllegalArgumentException("Rotation ID '" + name + "' is missing or has invalid format");
            }
            RotationName rotation = RotationName.from(name);
            if (rotations.contains(rotation)) {
                throw new IllegalArgumentException("Rotation ID '" + name + "' is duplicated");
            }
            rotations.add(rotation);
        }
        return Collections.unmodifiableSet(rotations);
    }

}
