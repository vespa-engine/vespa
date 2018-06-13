// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ExportPackageAnnotation {
    private final int major;
    private final int minor;
    private final int micro;
    private final String qualifier;

    private static final Pattern QUALIFIER_PATTERN = Pattern.compile("[\\p{Alpha}\\p{Digit}_-]*");

    public ExportPackageAnnotation(int major, int minor, int micro, String qualifier) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.qualifier = qualifier;

        requireNonNegative(major, "major");
        requireNonNegative(minor, "minor");
        requireNonNegative(micro, "micro");
        if (QUALIFIER_PATTERN.matcher(qualifier).matches() == false) {
            throw new IllegalArgumentException(
                    exportPackageError(String.format("qualifier must follow the format (alpha|digit|'_'|'-')* but was '%s'.", qualifier)));
        }
    }

    public String osgiVersion() {
        return String.format("%d.%d.%d", major, minor, micro) + (qualifier.isEmpty() ? "" : "." + qualifier);
    }

    private static String exportPackageError(String msg) {
        return "ExportPackage anntotation: " + msg;
    }

    private static void requireNonNegative(int i, String fieldName) {
        if (i < 0) {
            throw new IllegalArgumentException(exportPackageError(String.format("%s must be non-negative but was %d.", fieldName, i)));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ExportPackageAnnotation that = (ExportPackageAnnotation) o;
        return major == that.major && minor == that.minor && micro == that.micro && Objects.equals(qualifier, that.qualifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, micro, qualifier);
    }
}
