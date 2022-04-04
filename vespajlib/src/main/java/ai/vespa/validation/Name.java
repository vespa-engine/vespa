// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.validation;

import java.util.regex.Pattern;

/**
 * A name has from 1 to 64 {@link String} characters which may be letters, numbers,
 * dashes or underscores, and must start with a letter.
 *
 * Prefer domain-specific wrappers over this class, but prefer this over raw strings when possible.
 *
 * @author jonmv
 */
public class Name extends PatternedStringWrapper<Name> {

    static final Pattern namePattern = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

    private Name(String name) {
        super(name, namePattern, "name");
    }

    public static Name of(String value) {
        return new Name(value);
    }

}
