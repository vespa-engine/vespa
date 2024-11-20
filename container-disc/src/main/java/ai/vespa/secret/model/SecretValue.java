package ai.vespa.secret.model;

/**
 * @author gjoranv
 */
public record SecretValue(String value) {

    private static final int MAX_LENGTH = 64*1024;

    public SecretValue {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Secret value cannot be null or empty");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Secret value is too long");
        }
    }

    public static SecretValue of(String value) {
        return new SecretValue(value);
    }

}
