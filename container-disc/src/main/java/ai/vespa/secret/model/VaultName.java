package ai.vespa.secret.model;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * @author gjoranv
 */
public class VaultName extends PatternedStringWrapper<VaultName> {

    // Each dot-separated segment must start with an alphanumeric or underscore, matching Athenz' EntityName pattern.
    private static final Pattern namePattern =
            Pattern.compile("(?=.{1,64}$)[a-zA-Z0-9_][a-zA-Z0-9_-]*(\\.[a-zA-Z0-9_][a-zA-Z0-9_-]*)*");

    private VaultName(String name) {
            super(name, namePattern, "Vault name");
        }

    public static VaultName of(String name) {
            return new VaultName(name);
        }

}
