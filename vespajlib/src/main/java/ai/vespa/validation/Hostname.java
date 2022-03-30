// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.validation;

import java.util.regex.Pattern;

import static ai.vespa.validation.Validation.requireMatch;

/**
 * A valid hostname.
 *
 * @author jonmv
 */
public class Hostname extends StringWrapper<Hostname> {

    public static final Pattern hostnameLabel = Pattern.compile("([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9-]{0,61}[A-Za-z0-9])");
    public static final Pattern hostnamePattern = Pattern.compile("(?=.{1,255})(" + hostnameLabel + "\\.)*" + hostnameLabel);

    private Hostname(String value) {
        super(value);
    }

    public static Hostname of(String hostname) {
        return new Hostname(requireMatch(hostname, "hostname", hostnamePattern));
   }

    public static String requireLabel(String label) {
        return requireMatch(label, "hostname label", hostnameLabel);
    }

}
