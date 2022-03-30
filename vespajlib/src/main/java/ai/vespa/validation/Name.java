// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.validation;

import java.util.regex.Pattern;

import static ai.vespa.validation.Validation.requireMatch;

/**
 * A name is a non-null, non-blank {@link String} which starts with a letter, and has up to
 * 254 more characters which may be letters, numbers, dashes or underscores.
 *
 * Prefer domain-specific wrappers over this class, but prefer this over raw strings when possible. gT
 *
 * @author jonmv
 */
public class Name extends StringWrapper<Name> {

    public static final Pattern namePattern = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,254}");

    private Name(String value) {
        super(value);
    }

    public static Name of(String value) {
        return of(value, "name");
    };

    public static Name of(String value, String description) {
        return new Name(requireMatch(value, description, namePattern));
    };

}
