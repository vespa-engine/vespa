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

    public String forVault(VaultName vault) {
        return switch(this) {
            case WRITER, READER -> vault.value() + "-" + value;
            case TENANT_SECRET_WRITER -> value;
        };
    }

    @Override
    public String toString() {
        return Role.class.getSimpleName() + "." + value;
    }

}
