// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.http;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

import static ai.vespa.validation.Validation.requireLength;
import static ai.vespa.validation.Validation.requireMatch;

/**
 * A valid domain name, which can be used in a {@link java.net.URI}.
 *
 * @author jonmv
 */
public class DomainName extends PatternedStringWrapper<DomainName> {

    protected static final Pattern labelPattern = Pattern.compile("([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9-]{0,61}[A-Za-z0-9])");
    protected static final Pattern domainNamePattern = Pattern.compile("(" + labelPattern + "\\.)*" + labelPattern + "\\.?");

    public static final DomainName localhost = DomainName.of("localhost");

    protected DomainName(String value, String description) {
        super(requireLength(value, "domain name length", 1, 255), domainNamePattern, description);
    }

    public static DomainName of(String value) {
        return new DomainName(value, "domain name");
   }

    public static String requireLabel(String label) {
        return requireMatch(label, "domain name label", labelPattern);
    }

}
