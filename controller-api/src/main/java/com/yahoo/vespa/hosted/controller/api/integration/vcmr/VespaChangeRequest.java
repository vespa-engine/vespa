// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.vcmr;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author olaa
 */
public class VespaChangeRequest extends ChangeRequest {

    private final Status status;
    private final ZoneId zoneId;
    // TODO: Create applicationActionPlan
    private final List<HostAction> hostActionPlan;

    public VespaChangeRequest(String id, ChangeRequestSource changeRequestSource, List<String> impactedSwitches, List<String> impactedHosts, Approval approval, Impact impact, Status status, List<HostAction> hostActionPlan, ZoneId zoneId) {
        super(id, changeRequestSource, impactedSwitches, impactedHosts, approval, impact);
        this.status = status;
        this.hostActionPlan = hostActionPlan;
        this.zoneId = zoneId;
    }
    public VespaChangeRequest(ChangeRequest changeRequest, ZoneId zoneId) {
        this(changeRequest.getId(), changeRequest.getChangeRequestSource(), changeRequest.getImpactedSwitches(),
                changeRequest.getImpactedHosts(), changeRequest.getApproval(), changeRequest.getImpact(), Status.PENDING_ASSESSMENT, List.of(), zoneId);
    }

    public Status getStatus() {
        return status;
    }

    public List<HostAction> getHostActionPlan() {
        return hostActionPlan;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public VespaChangeRequest withStatus(Status status) {
        return new VespaChangeRequest(getId(), getChangeRequestSource(), getImpactedSwitches(), getImpactedHosts(), getApproval(), getImpact(), status, hostActionPlan, zoneId);
    }

    public VespaChangeRequest withSource(ChangeRequestSource source) {
        return new VespaChangeRequest(getId(), source, getImpactedSwitches(), getImpactedHosts(), getApproval(), getImpact(), status, hostActionPlan, zoneId);
    }

    public VespaChangeRequest withImpact(Impact impact) {
        return new VespaChangeRequest(getId(), getChangeRequestSource(), getImpactedSwitches(), getImpactedHosts(), getApproval(), impact, status, hostActionPlan, zoneId);
    }

    public VespaChangeRequest withApproval(Approval approval) {
        return new VespaChangeRequest(getId(), getChangeRequestSource(), getImpactedSwitches(), getImpactedHosts(), approval, getImpact(), status, hostActionPlan, zoneId);
    }

    public VespaChangeRequest withActionPlan(List<HostAction> hostActionPlan) {
        return new VespaChangeRequest(getId(), getChangeRequestSource(), getImpactedSwitches(), getImpactedHosts(), getApproval(), getImpact(), status, hostActionPlan, zoneId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        VespaChangeRequest that = (VespaChangeRequest) o;
        return status == that.status &&
                Objects.equals(hostActionPlan, that.hostActionPlan) &&
                Objects.equals(zoneId, that.zoneId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), status, hostActionPlan, zoneId);
    }

    @Override
    public String toString() {
        return "VespaChangeRequest{" +
                "id='" + getId() + '\'' +
                ", changeRequestSource=" + getChangeRequestSource() +
                ", impactedSwitches=" + getImpactedSwitches() +
                ", impactedHosts=" + getImpactedHosts() +
                ", approval=" + getApproval() +
                ", impact=" + getImpact() +
                ", status=" + status +
                ", zoneId=" + zoneId +
                ", hostActionPlan=" + hostActionPlan +
                '}';
    }

    public enum Status {
        COMPLETED,
        READY,
        IN_PROGRESS,
        PENDING_ACTION,
        PENDING_ASSESSMENT,
        REQUIRES_OPERATOR_ACTION,
        OUT_OF_SYNC,
        NOOP
    }
}
