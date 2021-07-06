package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.controller.api.integration.resource.CostInfo;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.stream.Stream;

public class PlanRegistryMock implements PlanRegistry {

    private final Plan freeTrial = new MockPlan("trial", false, 0, 0, 0, 200, "Free Trial - for testing purposes");
    private final Plan paidPlan  = new MockPlan("paid", true, 3, 6, 9, 500, "Paid Plan - for testing purposes");
    private final Plan nonePlan  = new MockPlan("none", false, 0, 0, 0, 0, "None Plan - for testing purposes");

    @Override
    public Plan defaultPlan() {
        return freeTrial;
    }

    @Override
    public Optional<Plan> plan(PlanId planId) {
        return Stream.of(freeTrial, paidPlan, nonePlan)
                .filter(p -> p.id().equals(planId))
                .findAny();
    }

    private static class MockPlan implements Plan {
        private final PlanId planId;
        private final String description;
        private final CostCalculator costCalculator;
        private final QuotaCalculator quotaCalculator;
        private final boolean billed;

        public MockPlan(String planId, boolean billed, int cpuPrice, int memPrice, int dgbPrice, int quota, String description) {
            this.planId = PlanId.from(planId);
            this.description = description;
            this.costCalculator = new MockCostCalculator(BigDecimal.valueOf(cpuPrice), BigDecimal.valueOf(memPrice), BigDecimal.valueOf(dgbPrice));
            this.quotaCalculator = () -> Quota.unlimited().withBudget(quota);
            this.billed = billed;
        }

        @Override
        public PlanId id() {
            return planId;
        }

        @Override
        public String displayName() {
            return description;
        }

        @Override
        public CostCalculator calculator() {
            return costCalculator;
        }

        @Override
        public QuotaCalculator quota() {
            return quotaCalculator;
        }

        @Override
        public boolean isBilled() {
            return billed;
        }
    }

    private static class MockCostCalculator implements CostCalculator {
        private static final BigDecimal millisPerHour = BigDecimal.valueOf(60 * 60 * 1000);
        private final BigDecimal cpuHourCost;
        private final BigDecimal memHourCost;
        private final BigDecimal dgbHourCost;

        public MockCostCalculator() {
            this(BigDecimal.valueOf(3, 2),
                 BigDecimal.valueOf(5, 3),
                 BigDecimal.valueOf(3, 4));
        }

        public MockCostCalculator(BigDecimal cpuPrice, BigDecimal memPrice, BigDecimal dgbPrice) {
            this.cpuHourCost = cpuPrice;
            this.memHourCost = memPrice;
            this.dgbHourCost = dgbPrice;
        }

        @Override
        public CostInfo calculate(ResourceUsage usage) {
            var cpuCost = usage.getCpuMillis().divide(millisPerHour, RoundingMode.HALF_UP).multiply(cpuHourCost);
            var memCost = usage.getMemoryMillis().divide(millisPerHour, RoundingMode.HALF_UP).multiply(memHourCost);
            var dgbCost = usage.getDiskMillis().divide(millisPerHour, RoundingMode.HALF_UP).multiply(dgbHourCost);

            return new CostInfo(
                    usage.getApplicationId(),
                    usage.getZoneId(),
                    usage.getCpuMillis().divide(millisPerHour, RoundingMode.HALF_UP),
                    usage.getMemoryMillis().divide(millisPerHour, RoundingMode.HALF_UP),
                    usage.getDiskMillis().divide(millisPerHour, RoundingMode.HALF_UP),
                    cpuCost,
                    memCost,
                    dgbCost
            );
        }

        @Override
        public double calculate(NodeResources resources) {
            return resources.cost();
        }
    }
}
