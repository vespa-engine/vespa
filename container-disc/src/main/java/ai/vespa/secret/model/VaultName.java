package ai.vespa.secret.model;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * @author gjoranv
 */
public class VaultName extends PatternedStringWrapper<VaultName> {

        private static final Pattern namePattern = Pattern.compile("[.a-zA-Z0-9_-]{1,64}");

        private VaultName(String name) {
            super(name, namePattern, "Vault name");
        }

        public static VaultName of(String name) {
            return new VaultName(name);
        }

}
