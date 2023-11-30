// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics;

/**
 * @author yngveaasheim
 */
public enum ControllerMetrics implements VespaMetrics {

    ATHENZ_REQUEST_ERROR("athenz.request.error", Unit.REQUEST, "Controller: Athenz request error"),
    ARCHIVE_BUCKET_COUNT("archive.bucketCount", Unit.BUCKET, "Controller: Archive bucket count"),

    DEPLOYMENT_JOBS_QUEUED("deployment.jobsQueued", Unit.TASK, "The number of deployment jobs queued"),
    DEPLOYMENT_JOBS_ACTIVE("deployment.jobsActive", Unit.TASK, "The number of deployment jobs active"),
    DEPLOYMENT_EXECUTOR_SIZE("deployment.executorSize", Unit.THREAD, "The number of deployment jobs that can run in parallel"),
    DEPLOYMENT_START("deployment.start", Unit.DEPLOYMENT, "The number of started deployment jobs"),
    DEPLOYMENT_NODE_ALLOCATION_FAILURE("deployment.nodeAllocationFailure", Unit.DEPLOYMENT, "The number of deployments failed due to node allocation failures"),
    DEPLOYMENT_ENDPOINT_CERTIFICATE_TIMEOUT("deployment.endpointCertificateTimeout", Unit.DEPLOYMENT, "The number of deployments failed due to timeout acquiring endpoint certificate"),
    DEPLOYMENT_DEPLOYMENT_FAILURE("deployment.deploymentFailure", Unit.DEPLOYMENT, "The number of deployments that failed"),
    DEPLOYMENT_INVALID_APPLICATION("deployment.invalidApplication", Unit.DEPLOYMENT, "Deployments with invalid application package"),
    DEPLOYMENT_CONVERGENCE_FAILURE("deployment.convergenceFailure", Unit.DEPLOYMENT, "The number of deployments with convergence failure"),
    DEPLOYMENT_TEST_FAILURE("deployment.testFailure", Unit.DEPLOYMENT, "The number of test deployments with test failure"),
    DEPLOYMENT_NO_TESTS("deployment.noTests", Unit.DEPLOYMENT, "Deployments with no tests"),
    DEPLOYMENT_ERROR("deployment.error", Unit.DEPLOYMENT, "Deployments with error"),
    DEPLOYMENT_ABORT("deployment.abort", Unit.DEPLOYMENT, "Deployments that were aborted"),
    DEPLOYMENT_CANCEL("deployment.cancel", Unit.DEPLOYMENT, "Deployments that were canceled"),
    DEPLOYMENT_SUCCESS("deployment.success", Unit.DEPLOYMENT, "Successful deployments"),
    DEPLOYMENT_QUOTA_EXCEEDED("deployment.quotaExceeded", Unit.DEPLOYMENT, "Deployments stopped due to exceeding quota"),
    BILLING_TENANTS("billing.tenants", Unit.TENANT, "Billing tenants"),
    DEPLOYMENT_FAILURE_PERCENTAGE("deployment.failurePercentage", Unit.PERCENTAGE, "Deployment: Failure percentage"),
    DEPLOYMENT_DURATION("deployment.duration", Unit.SECOND, "Deployment duration"),
    DEPLOYMENT_AVERAGE_DURATION("deployment.averageDuration", Unit.SECOND, "Deployment duration averaged for each application"),
    DEPLOYMENT_FAILING_UPGRADES("deployment.failingUpgrades", Unit.DEPLOYMENT, "Deployment: Failing upgrades"),
    DEPLOYMENT_BUILD_AGE_SECONDS("deployment.buildAgeSeconds", Unit.SECOND, "Deployment: The age of a build deployed"),
    DEPLOYMENT_WARNINGS("deployment.warnings", Unit.ITEM, "The number of application related warnings during deployments"),
    DEPLOYMENT_OVERDUE_UPGRADE_SECONDS("deployment.overdueUpgradeSeconds", Unit.SECOND, "Deployment: Overdue upgrade period"),
    DEPLOYMENT_OS_CHANGE_DURATION("deployment.osChangeDuration", Unit.SECOND, "Deployment: OS change duration"),
    DEPLOYMENT_PLATFORM_CHANGE_DURATION("deployment.platformChangeDuration", Unit.SECOND, "Deployment: Platform change duration"),
    DEPLOYMENT_NODE_COUNT_BY_OS_VERSION("deployment.nodeCountByOsVersion", Unit.NODE, "Deployment: Node count by OS version"),
    DEPLOYMENT_NODE_COUNT_BY_PLATFORM_VERSION("deployment.nodeCountByPlatformVersion", Unit.NODE, "Deployment: Node count by platform version"),
    DEPLOYMENT_BROKEN_SYSTEM_VERSION("deployment.brokenSystemVersion", Unit.BINARY, "Deployment: Value 1 for broken system versions, 0 if not"),
    REMAINING_ROTATIONS("remaining_rotations", Unit.ROTATION, "Remaining rotations"),
    DNS_QUEUED_REQUESTS("dns.queuedRequests", Unit.REQUEST, "Queued DNS requests"),
    ZMS_QUOTA_USAGE("zms.quota.usage", Unit.FRACTION, "ZMS Quota usage per resource type"),
    COREDUMP_PROCESSED("coredump.processed", Unit.FAILURE,"Controller: Core dumps processed"),
    AUTH0_EXCEPTIONS("auth0.exceptions", Unit.FAILURE, "Controller: Auth0 exceptions"),
    CERTIFICATE_POOL_AVAILABLE("certificate_pool_available", Unit.FRACTION, "Available certificates in the pool, fraction of configured size"),
    BILLING_EXCEPTIONS("billing.exceptions", Unit.FAILURE, "Controller: Billing related exceptions"),
    BILLING_WEBHOOK_FILTER_FAILURES("billing.webhook.failures", Unit.FAILURE, "Controller: webhook filter failures"),
    BILLING_WEBHOOK_FILTER_REQUESTS("billing.webhook.requests", Unit.REQUEST, "Controller: webhook filter requests"),

    // Metrics per API, metrics names generated in ControllerMaintainer/MetricsReporter
    OPERATION_APPLICATION("operation.application", Unit.REQUEST, "Controller: Requests for /application API"),
    OPERATION_CHANGEMANAGEMENT("operation.changemanagement", Unit.REQUEST, "Controller: Requests for /changemanagement API"),
    OPERATION_CONFIGSERVER("operation.configserver", Unit.REQUEST, "Controller: Requests for /configserver API"),
    OPERATION_CONTROLLER("operation.controller", Unit.REQUEST, "Controller: Requests for /controller API"),
    OPERATION_FLAGS("operation.flags", Unit.REQUEST, "Controller: Requests for /flags API"),
    OPERATION_OS("operation.os", Unit.REQUEST, "Controller: Requests for /os API"),
    OPERATION_ROUTING("operation.routing", Unit.REQUEST, "Controller: Requests for /routing API"),
    OPERATION_ZONE("operation.zone", Unit.REQUEST, "Controller: Requests for /zone API"),

    // Metering metrics - not used - TODO: remove from controller code.
    METERING_AGE_SECONDS("metering.age.seconds", Unit.SECOND, "Controller: Metering age seconds"),
    METERING_COST_HOURLY("metering.cost.hourly", Unit.DOLLAR_PER_HOUR, "Controller: Metering cost hourly"),
    METERING_DISK_GB("metering.diskGB", Unit.GIGABYTE, "Controller: Metering disk GB"),
    METERING_MEMORY_GB("metering.memoryGB", Unit.GIGABYTE, "Controller: Metering memory GB"),
    METERING_VCPU("metering.vcpu", Unit.VCPU, "Controller: Metering VCPU"),
    METERING_LAST_REPORTED("metering_last_reported", Unit.SECONDS_SINCE_EPOCH, "Controller: Metering last reported"),
    METERING_TOTAL_REPORTED("metering_total_reported", Unit.ITEM, "Controller: Metering total reported (sum of resources)"),
    METERING_LAST_REFRESH("metering_last_refresh", Unit.SECONDS_SINCE_EPOCH, "Controller: Last refresh time"),

    MAIL_SENT("mail.sent", Unit.OPERATION, "Mail sent"),
    MAIL_FAILED("mail.failed", Unit.OPERATION, "Mail delivery failed"),
    MAIL_THROTTLED("mail.throttled", Unit.OPERATION, "Mail delivery throttled");


    private final String name;
    private final Unit unit;
    private final String description;

    ControllerMetrics(String name, Unit unit, String description) {
        this.name = name;
        this.unit = unit;
        this.description = description;
    }

    public String baseName() {
        return name;
    }

    public Unit unit() {
        return unit;
    }

    public String description() {
        return description;
    }

}
