package ai.vespa.secret.model;

/**
 * @author gjoranv
 */
public enum Role {

    READER("reader"),
    WRITER("writer");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String forVault(VaultName vault) {
        return vault.value() + "-" + value;
    }

    @Override
    public String toString() {
        return Role.class.getSimpleName() + "." + value;
    }

}
