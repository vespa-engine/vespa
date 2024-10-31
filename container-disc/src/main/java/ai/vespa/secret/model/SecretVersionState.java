package ai.vespa.secret.model;

import java.util.logging.Logger;

/**
 * @author gjoranv
 */
public enum SecretVersionState {

    // DO NOT CHANGE THE ORDERING OF THESE ENUMS
    // They are used to sort lists of secret versions returned by the secret store.
    PENDING("PENDING", "Pending"),   // Maps to AWSPENDING
    CURRENT("CURRENT", "Current"),   // Maps to AWSCURRENT
    PREVIOUS("PREVIOUS", "Previous"), // Maps to AWSPREVIOUS
    DEPRECATED("DEPRECATED", "Deprecated");
    // Deprecated versions have no staging labels in ASM, and will be garbage collected automatically

    private static final Logger log = Logger.getLogger(SecretVersionState.class.getName());

    private final String serializedName;
    private final String prettyName;

    SecretVersionState(String serializedName, String prettyName) {
        this.serializedName =serializedName;
        this.prettyName = prettyName;
    }

    public String serialize() {
        return serializedName;
    }

    public String prettyName() {
        return prettyName;
    }

    // Ensure that toString cannot be directly used for serialization
    @Override
    public String toString() {
        return SecretVersionState.class.getSimpleName() + "." + serializedName;
    }

    public static SecretVersionState deserialize(String serializedName) {
        for (SecretVersionState state : values()) {
            if (state.serializedName.equals(serializedName))
                return state;
        }
        throw new IllegalArgumentException("No such secret version state: " + serializedName);
    }

    public void validateTransition(SecretVersionState newState) {
        if (this == newState) {
            log.fine("Transition to the same state: " + newState);
        }
        if (this == PREVIOUS && newState != DEPRECATED) {
            throw new IllegalArgumentException("Cannot transition from PREVIOUS state to " + newState);
        }
        if (this == DEPRECATED) {
            throw new IllegalArgumentException("Cannot transition from DEPRECATED state: " + this);
        }
        if (newState == PENDING) {
            throw new IllegalArgumentException("Cannot transition to PENDING state: " + this + " -> " + newState);
        }
    }

}
