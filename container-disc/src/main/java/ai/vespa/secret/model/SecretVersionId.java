package ai.vespa.secret.model;

/**
 * @author gjoranv
 */
public record SecretVersionId(String value) {

    public SecretVersionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Version id cannot be null or empty");
        }
    }

    public static SecretVersionId of(String value) {
        return new SecretVersionId(value);
    }

}
