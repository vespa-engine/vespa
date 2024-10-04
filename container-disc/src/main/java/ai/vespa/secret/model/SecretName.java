package ai.vespa.secret.model;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * @author gjoranv
 */
public class SecretName extends PatternedStringWrapper<SecretName> {

    // TODO: reset max size to 64 when we have stopped using concatenated vault+secret names
    private static final Pattern namePattern = Pattern.compile("[.a-zA-Z0-9_-]{1,128}");

    private SecretName(String name) {
        super(name, namePattern, "Secret name");
    }

    public static SecretName of(String name) {
        return new SecretName(name);
    }

}
