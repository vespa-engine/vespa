// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.validation;

import java.util.regex.Pattern;

import static ai.vespa.validation.Validation.requireMatch;

/**
 * Helper to easily create {@link StringWrapper} classes whose contents match a specific regex.
 *
 * @author jonmv
 */
public abstract class PatternedStringWrapper<T extends PatternedStringWrapper<T>> extends StringWrapper<T> {

    protected PatternedStringWrapper(String value, Pattern pattern, String description) {
        super(requireMatch(value, description, pattern));
    }

}
