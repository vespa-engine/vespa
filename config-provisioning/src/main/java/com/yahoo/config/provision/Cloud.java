// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * Represents a cloud service and its supported features.
 *
 * @author mpolden
 */
public class Cloud {

    private final CloudName name;

    private final boolean dynamicProvisioning;
    private final boolean allowHostSharing;
    private final boolean reprovisionToUpgradeOs;
    private final boolean requireAccessControl;

    protected Cloud(CloudName name, boolean dynamicProvisioning, boolean allowHostSharing, boolean reprovisionToUpgradeOs,
                    boolean requireAccessControl) {
        this.name = name;
        this.dynamicProvisioning = dynamicProvisioning;
        this.allowHostSharing = allowHostSharing;
        this.reprovisionToUpgradeOs = reprovisionToUpgradeOs;
        this.requireAccessControl = requireAccessControl;
    }

    /** The name of this */
    public CloudName name() {
        return name;
    }

    /** Returns whether this can provision hosts dynamically */
    public boolean dynamicProvisioning() {
        return dynamicProvisioning;
    }

    /** Returns wheter this allows different applications to share the same host */
    public boolean allowHostSharing() {
        return allowHostSharing;
    }

    /** Returns whether upgrading OS on hosts in this requires the host to be reprovisioned */
    public boolean reprovisionToUpgradeOs() {
        return reprovisionToUpgradeOs;
    }

    /** Returns whether to require access control for all clusters in this */
    public boolean requireAccessControl() {
        return requireAccessControl;
    }

    public Cloud withDynamicProvisioning(boolean dynamicProvisioning) {
        return new Cloud(name, dynamicProvisioning, allowHostSharing, reprovisionToUpgradeOs, requireAccessControl);
    }

    public Cloud withAllowHostSharing(boolean allowHostSharing) {
        return new Cloud(name, dynamicProvisioning, allowHostSharing, reprovisionToUpgradeOs, requireAccessControl);
    }

    public Cloud withReprovisionToUpgradeOs(boolean reprovisionToUpgradeOs) {
        return new Cloud(name, dynamicProvisioning, allowHostSharing, reprovisionToUpgradeOs, requireAccessControl);
    }

    public Cloud withRequireAccessControl(boolean requireAccessControl) {
        return new Cloud(name, dynamicProvisioning, allowHostSharing, reprovisionToUpgradeOs, requireAccessControl);
    }

    /** For testing purposes only */
    public static Cloud defaultCloud() {
        return new Cloud(CloudName.defaultName(), false, true, false, false);
    }

    @Override
    public String toString() {
        return "cloud " + name;
    }

}
