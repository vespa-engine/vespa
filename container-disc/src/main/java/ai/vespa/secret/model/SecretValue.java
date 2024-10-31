package ai.vespa.secret.model;

/**
 * @author gjoranv
 */
public record SecretValue(String value) {

    public SecretValue {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Secret value cannot be null or empty");
        }
    }

    public static SecretValue of(String value) {
        return new SecretValue(value);
    }

}
