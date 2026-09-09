package ai.vespa.secret.model;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * @author gjoranv
 */
public class SecretName extends PatternedStringWrapper<SecretName> {

    // TODO: reset max size to 64 when we have stopped using concatenated vault+secret names
    // Each dot-separated segment must start with an alphanumeric or underscore, matching Athenz' EntityName pattern.
    private static final Pattern namePattern =
            Pattern.compile("(?=.{1,128}$)[a-zA-Z0-9_][a-zA-Z0-9_-]*(\\.[a-zA-Z0-9_][a-zA-Z0-9_-]*)*");

    private SecretName(String name) {
        super(name, namePattern, "Secret name");
    }

    public static SecretName of(String name) {
        return new SecretName(name);
    }

    public static boolean isValid(String name) {
        return namePattern.matcher(name).matches();
    }

}
