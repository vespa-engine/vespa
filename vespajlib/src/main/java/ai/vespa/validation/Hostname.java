// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.validation;

import static ai.vespa.validation.Validation.requireInRange;
import static ai.vespa.validation.Validation.requireMatch;

/**
 * A valid hostname, matching {@link DomainName#domainNamePattern}, and no more than 64 characters in length.
 *
 * @author jonmv
 */
public class Hostname extends StringWrapper<Hostname> {

    private Hostname(String value) {
        super(value);
    }

    public static Hostname of(String value) {
        requireInRange(value.length(), "hostname length", 1, 64);
        return new Hostname(requireMatch(value, "hostname", DomainName.domainNamePattern));
    }

}
