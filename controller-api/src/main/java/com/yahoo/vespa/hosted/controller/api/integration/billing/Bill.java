// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;


/**
 * An Bill is an identifier with a status (with history) and line items.  A line item is the meat and
 * potatoes of the content of the bill, and are a history of items.  Most line items are connected to
 * a given deployment in Vespa Cloud, but they can also be manually added to e.g. give a discount or represent
 * support.
 * <p>
 * All line items have a Plan associated with them - which was used to map from utilization to an actual price.
 * <p>
 * The bill has a status history, but only the latest status is exposed through this API.
 *
 * @author ogronnesby
 */
public class Bill {
    private static final BigDecimal SCALED_ZERO = new BigDecimal("0.00");

    private final Id id;
    private final TenantName tenant;
    private final List<LineItem> lineItems;
    private final StatusHistory statusHistory;
    private final ZonedDateTime startTime;
    private final ZonedDateTime endTime;

    public Bill(Id id, TenantName tenant, StatusHistory statusHistory, List<LineItem> lineItems, ZonedDateTime startTime, ZonedDateTime endTime) {
        this.id = id;
        this.tenant = tenant;
        this.lineItems = List.copyOf(lineItems);
        this.statusHistory = statusHistory;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Id id() {
        return id;
    }

    public TenantName tenant() {
        return tenant;
    }

    public String status() {
        return statusHistory.current();
    }

    public StatusHistory statusHistory() {
        return statusHistory;
    }

    public List<LineItem> lineItems() {
        return lineItems;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public ZonedDateTime getEndTime() {
        return endTime;
    }

    public LocalDate getStartDate() {
        return startTime.toLocalDate();
    }

    public LocalDate getEndDate() {
        return endTime.minusDays(1).toLocalDate();
    }

    public BigDecimal sum() {
        return lineItems.stream().map(LineItem::amount).reduce(SCALED_ZERO, BigDecimal::add);
    }

    public BigDecimal sumCpuHours() {
        return sumResourceValues(LineItem::getCpuHours);
    }

    public BigDecimal sumMemoryHours() {
        return sumResourceValues(LineItem::getMemoryHours);
    }

    public BigDecimal sumDiskHours() {
        return sumResourceValues(LineItem::getDiskHours);
    }

    public BigDecimal sumCpuCost() {
        return sumResourceValues(LineItem::getCpuCost);
    }

    public BigDecimal sumMemoryCost() {
        return sumResourceValues(LineItem::getMemoryCost);
    }

    public BigDecimal sumDiskCost() {
        return sumResourceValues(LineItem::getDiskCost);
    }

    public BigDecimal sumAdditionalCost() {
        // anything that is not covered by the cost for resources is "additional" costs
        var resourceCosts = sumCpuCost().add(sumMemoryCost()).add(sumDiskCost());
        return sum().subtract(resourceCosts);
    }

    private BigDecimal sumResourceValues(Function<LineItem, Optional<BigDecimal>> f) {
        return lineItems.stream().flatMap(li -> f.apply(li).stream()).reduce(SCALED_ZERO, BigDecimal::add);
    }

    public static final class Id {
        private final String value;

        public static Id of(String value) {
            Objects.requireNonNull(value);
            return new Id(value);
        }

        public static Id generate() {
            var id = UUID.randomUUID().toString();
            return new Id(id);
        }

        private Id(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Id billId = (Id) o;
            return value.equals(billId.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return "BillId{" +
                    "value='" + value + '\'' +
                    '}';
        }
    }

    /**
     * Represents a chargeable line on a bill.
     */
    public static class LineItem {
        private final String id;
        private final String description;
        private final BigDecimal amount;
        private final String plan;
        private final String agent;
        private final ZonedDateTime addedAt;
        private ZonedDateTime startedAt;
        private ZonedDateTime endedAt;
        private ApplicationId applicationId;
        private ZoneId zoneId;
        private BigDecimal cpuHours;
        private BigDecimal memoryHours;
        private BigDecimal diskHours;
        private BigDecimal cpuCost;
        private BigDecimal memoryCost;
        private BigDecimal diskCost;
        private NodeResources.Architecture architecture;

        public LineItem(String id, String description, BigDecimal amount, String plan, String agent, ZonedDateTime addedAt) {
            this.id = id;
            this.description = description;
            this.amount = amount;
            this.plan = plan;
            this.agent = agent;
            this.addedAt = addedAt;
        }

        public LineItem(String id, String description, BigDecimal amount, String plan, String agent, ZonedDateTime addedAt, ZonedDateTime startedAt, ZonedDateTime endedAt, ApplicationId applicationId, ZoneId zoneId,
                        BigDecimal cpuHours, BigDecimal memoryHours, BigDecimal diskHours, BigDecimal cpuCost, BigDecimal memoryCost, BigDecimal diskCost, NodeResources.Architecture architecture) {
            this(id, description, amount, plan, agent, addedAt);
            this.startedAt = startedAt;
            this.endedAt = endedAt;

            if (applicationId == null && zoneId != null)
                throw new IllegalArgumentException("Must supply applicationId if zoneId is supplied");

            this.applicationId = applicationId;
            this.zoneId = zoneId;
            this.cpuHours = cpuHours;
            this.memoryHours = memoryHours;
            this.diskHours = diskHours;
            this.cpuCost = cpuCost;
            this.memoryCost = memoryCost;
            this.diskCost = diskCost;
            this.architecture = architecture;
        }

        /** The opaque ID of this */
        public String id() {
            return id;
        }

        /** The string description of this - used for display purposes */
        public String description() {
            return description;
        }

        /** The dollar amount of this */
        public BigDecimal amount() {
            return SCALED_ZERO.add(amount);
        }

        /** The plan used to calculate amount of this */
        public String plan() {
            return plan;
        }

        /** Who created this line item */
        public String agent() {
            return agent;
        }

        /** When was this line item added */
        public ZonedDateTime addedAt() {
            return addedAt;
        }

        /** What time period is this line item for - time start */
        public Optional<ZonedDateTime> startedAt() {
            return Optional.ofNullable(startedAt);
        }

        /** What time period is this line item for - time end */
        public Optional<ZonedDateTime> endedAt() {
            return Optional.ofNullable(endedAt);
        }

        /** Optionally - what application is this line item about */
        public Optional<ApplicationId> applicationId() {
            return Optional.ofNullable(applicationId);
        }

        /** Optionally - what zone deployment is this line item about */
        public Optional<ZoneId> zoneId() {
            return Optional.ofNullable(zoneId);
        }

        public Optional<BigDecimal> getCpuHours() {
            return Optional.ofNullable(cpuHours);
        }

        public Optional<BigDecimal> getMemoryHours() {
            return Optional.ofNullable(memoryHours);
        }

        public Optional<BigDecimal> getDiskHours() {
            return Optional.ofNullable(diskHours);
        }

        public Optional<BigDecimal> getCpuCost() {
            return Optional.ofNullable(cpuCost);
        }

        public Optional<BigDecimal> getMemoryCost() {
            return Optional.ofNullable(memoryCost);
        }

        public Optional<BigDecimal> getDiskCost() {
            return Optional.ofNullable(diskCost);
        }

        public Optional<NodeResources.Architecture> getArchitecture() {
            return Optional.ofNullable(architecture);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LineItem lineItem = (LineItem) o;
            return id.equals(lineItem.id) &&
                    description.equals(lineItem.description) &&
                    amount.equals(lineItem.amount) &&
                    plan.equals(lineItem.plan) &&
                    agent.equals(lineItem.agent) &&
                    addedAt.equals(lineItem.addedAt) &&
                    startedAt.equals(lineItem.startedAt) &&
                    endedAt.equals(lineItem.endedAt) &&
                    applicationId.equals(lineItem.applicationId) &&
                    zoneId.equals(lineItem.zoneId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, description, amount, plan, agent, addedAt, startedAt, endedAt, applicationId, zoneId);
        }

        @Override
        public String toString() {
            return "LineItem{" +
                    "id='" + id + '\'' +
                    ", description='" + description + '\'' +
                    ", amount=" + amount +
                    ", plan='" + plan + '\'' +
                    ", agent='" + agent + '\'' +
                    ", addedAt=" + addedAt +
                    ", startedAt=" + startedAt +
                    ", endedAt=" + endedAt +
                    ", applicationId=" + applicationId +
                    ", zoneId=" + zoneId +
                    '}';
        }
    }

    public static class StatusHistory {
        SortedMap<ZonedDateTime, String> history;

        public StatusHistory(SortedMap<ZonedDateTime, String> history) {
            this.history = history;
        }

        public static StatusHistory open(Clock clock) {
            var now = clock.instant().atZone(ZoneOffset.UTC);
            return new StatusHistory(
                    new TreeMap<>(Map.of(now, "OPEN"))
            );
        }

        public String current() {
            return history.get(history.lastKey());
        }

        public SortedMap<ZonedDateTime, String> getHistory() {
            return history;
        }

    }

}
