package ai.vespa.secret.model;

/**
 * @author gjoranv
 */
public enum Role {

    READER("reader"),
    WRITER("writer"),
    TENANT_SECRET_WRITER("tenant-secret-updater");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return Role.class.getSimpleName() + "." + value;
    }

}
