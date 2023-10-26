// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanId;
import com.yahoo.vespa.hosted.controller.notification.MailTemplating;
import com.yahoo.vespa.hosted.controller.notification.Notification;
import com.yahoo.vespa.hosted.controller.notification.NotificationSource;
import com.yahoo.vespa.hosted.controller.persistence.TrialNotifications;
import com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.persistence.TrialNotifications.State.EXPIRED;
import static com.yahoo.vespa.hosted.controller.persistence.TrialNotifications.State.EXPIRES_IMMEDIATELY;
import static com.yahoo.vespa.hosted.controller.persistence.TrialNotifications.State.EXPIRES_SOON;
import static com.yahoo.vespa.hosted.controller.persistence.TrialNotifications.State.MID_CHECK_IN;
import static com.yahoo.vespa.hosted.controller.persistence.TrialNotifications.State.SIGNED_UP;
import static com.yahoo.vespa.hosted.controller.persistence.TrialNotifications.State.UNKNOWN;

/**
 * Expires unused tenants from Vespa Cloud.
 *
 * @author ogronnesby
 */
public class CloudTrialExpirer extends ControllerMaintainer {
    private static final Logger log = Logger.getLogger(CloudTrialExpirer.class.getName());

    private static final Duration nonePlanAfter = Duration.ofDays(14);
    private static final Duration tombstoneAfter = Duration.ofDays(91);
    private final ListFlag<String> extendedTrialTenants;
    private final BooleanFlag cloudTrialNotificationEnabled;

    public CloudTrialExpirer(Controller controller, Duration interval) {
        super(controller, interval, null, SystemName.allOf(SystemName::isPublic));
        this.extendedTrialTenants = PermanentFlags.EXTENDED_TRIAL_TENANTS.bindTo(controller().flagSource());
        this.cloudTrialNotificationEnabled = Flags.CLOUD_TRIAL_NOTIFICATIONS.bindTo(controller().flagSource());
    }

    @Override
    protected double maintain() {
        var a = tombstoneNonePlanTenants();
        var b = moveInactiveTenantsToNonePlan();
        var c = notifyTenants();
        return (a ? 0.0 : -(1D/3)) + (b ? 0.0 : -(1D/3) + (c ? 0.0 : -(1D/3)));
    }

    private boolean moveInactiveTenantsToNonePlan() {
        var idleTrialTenants = controller().tenants().asList().stream()
                .filter(this::tenantIsCloudTenant)
                .filter(this::tenantIsNotExemptFromExpiry)
                .filter(this::tenantHasNoDeployments)
                .filter(this::tenantHasTrialPlan)
                .filter(tenantReadersNotLoggedIn(nonePlanAfter))
                .toList();

        if (! idleTrialTenants.isEmpty()) {
            var tenants = idleTrialTenants.stream().map(Tenant::name).map(TenantName::value).collect(Collectors.joining(", "));
            log.info("Setting tenants to 'none' plan: " + tenants);
        }

        return setPlanNone(idleTrialTenants);
    }

    private boolean tombstoneNonePlanTenants() {
        var idleOldPlanTenants = controller().tenants().asList().stream()
                .filter(this::tenantIsCloudTenant)
                .filter(this::tenantIsNotExemptFromExpiry)
                .filter(this::tenantHasNoDeployments)
                .filter(this::tenantHasNonePlan)
                .filter(tenantReadersNotLoggedIn(tombstoneAfter))
                .toList();

        if (! idleOldPlanTenants.isEmpty()) {
            var tenants = idleOldPlanTenants.stream().map(Tenant::name).map(TenantName::value).collect(Collectors.joining(", "));
            log.info("Setting tenants as tombstoned: " + tenants);
        }

        return tombstoneTenants(idleOldPlanTenants);
    }
    private boolean notifyTenants() {
        try {
            var currentStatus = controller().curator().readTrialNotifications()
                .map(TrialNotifications::tenants).orElse(List.of());
            log.fine(() -> "Current: %s".formatted(currentStatus));
            var currentStatusByTenant = new HashMap<TenantName, TrialNotifications.Status>();
            currentStatus.forEach(status -> currentStatusByTenant.put(status.tenant(), status));
            var updatedStatus = new ArrayList<TrialNotifications.Status>();
            var now = controller().clock().instant();

            for (var tenant : controller().tenants().asList()) {

                var status = currentStatusByTenant.get(tenant.name());
                var state = status == null ? UNKNOWN : status.state();
                var plan = controller().serviceRegistry().billingController().getPlan(tenant.name()).value();
                var ageInDays = Duration.between(tenant.createdAt(), now).toDays();

                // TODO Replace stubs with proper email content stored in templates.

                var enabled = cloudTrialNotificationEnabled.with(FetchVector.Dimension.TENANT_ID, tenant.name().value()).value();
                if (!enabled) {
                    if (status != null) updatedStatus.add(status);
                } else if (!List.of("none", "trial").contains(plan)) {
                    // Ignore tenants that are on a paid plan and skip from inclusion in updated data structure
                } else if (status == null && "trial".equals(plan) && ageInDays <= 1) {
                    updatedStatus.add(updatedStatus(tenant, now, SIGNED_UP));
                    notifySignup(tenant);
                } else if ("none".equals(plan) && !List.of(EXPIRED).contains(state)) {
                    updatedStatus.add(updatedStatus(tenant, now, EXPIRED));
                    notifyExpired(tenant);
                } else if ("trial".equals(plan) && ageInDays >= 13
                        && !List.of(EXPIRES_IMMEDIATELY, EXPIRED).contains(state)) {
                    updatedStatus.add(updatedStatus(tenant, now, EXPIRES_IMMEDIATELY));
                    notifyExpiresImmediately(tenant);
                } else if ("trial".equals(plan) && ageInDays >= 12
                        && !List.of(EXPIRES_SOON, EXPIRES_IMMEDIATELY, EXPIRED).contains(state)) {
                    updatedStatus.add(updatedStatus(tenant, now, EXPIRES_SOON));
                    notifyExpiresSoon(tenant);
                } else if ("trial".equals(plan) && ageInDays >= 7
                        && !List.of(MID_CHECK_IN, EXPIRES_SOON, EXPIRES_IMMEDIATELY, EXPIRED).contains(state)) {
                    updatedStatus.add(updatedStatus(tenant, now, MID_CHECK_IN));
                    notifyMidCheckIn(tenant);
                } else {
                    updatedStatus.add(status);
                }
            }
            log.fine(() -> "Updated: %s".formatted(updatedStatus));
            controller().curator().writeTrialNotifications(new TrialNotifications(updatedStatus));
            return true;
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to process trial notifications", e);
            return false;
        }
    }

    private void notifySignup(Tenant tenant) {
        var consoleMsg = "Welcome to Vespa Cloud trial! [Manage plan](%s)".formatted(billingUrl(tenant));
        queueNotification(tenant, consoleMsg, "Welcome to Vespa Cloud",
                          "Welcome to Vespa Cloud! We hope you will enjoy your trial. " +
                                  "Please reach out to us if you have any questions or feedback.");
    }

    private void notifyMidCheckIn(Tenant tenant) {
        var consoleMsg = "You're halfway through the **14 day** trial period. [Manage plan](%s)".formatted(billingUrl(tenant));
        queueNotification(tenant, consoleMsg, "How is your Vespa Cloud trial going?",
                          "How is your Vespa Cloud trial going? " +
                        "Please reach out to us if you have any questions or feedback.");
    }

    private void notifyExpiresSoon(Tenant tenant) {
        var consoleMsg = "Your Vespa Cloud trial expires in **2** days. [Manage plan](%s)".formatted(billingUrl(tenant));
        queueNotification(tenant, consoleMsg, "Your Vespa Cloud trial expires in 2 days",
                          "Your Vespa Cloud trial expires in 2 days. " +
                        "Please reach out to us if you have any questions or feedback.");
    }

    private void notifyExpiresImmediately(Tenant tenant) {
        var consoleMsg = "Your Vespa Cloud trial expires **tomorrow**. [Manage plan](%s)".formatted(billingUrl(tenant));
        queueNotification(tenant, consoleMsg, "Your Vespa Cloud trial expires tomorrow",
                          "Your Vespa Cloud trial expires tomorrow. " +
                        "Please reach out to us if you have any questions or feedback.");
    }

    private void notifyExpired(Tenant tenant) {
        var consoleMsg = "Your Vespa Cloud trial has expired. [Upgrade plan](%s)".formatted(billingUrl(tenant));
        queueNotification(tenant, consoleMsg, "Your Vespa Cloud trial has expired",
                          "Your Vespa Cloud trial has expired. " +
                        "Please reach out to us if you have any questions or feedback.");
    }

    private void queueNotification(Tenant tenant, String consoleMsg, String emailSubject, String emailMsg) {
        var mail = Optional.of(Notification.MailContent.fromTemplate(MailTemplating.Template.DEFAULT_MAIL_CONTENT)
                                       .subject(emailSubject)
                                       .with("mailMessageTemplate", "cloud-trial-notification")
                                       .with("cloudTrialMessage", emailMsg)
                                       .with("mailTitle", emailSubject)
                                       .with("consoleLink", controller().serviceRegistry().consoleUrls().tenantOverview(tenant.name()))
                                       .build());
        var source = NotificationSource.from(tenant.name());
        // Remove previous notification to ensure new notification is sent by email
        controller().notificationsDb().removeNotification(source, Notification.Type.account);
        controller().notificationsDb().setNotification(
                source, Notification.Type.account, Notification.Level.info, consoleMsg, List.of(), mail);
    }

    private String billingUrl(Tenant t) { return controller().serviceRegistry().consoleUrls().tenantBilling(t.name()); }

    private static TrialNotifications.Status updatedStatus(Tenant t, Instant i, TrialNotifications.State s) {
        return new TrialNotifications.Status(t.name(), s, i);
    }

    private boolean tenantIsCloudTenant(Tenant tenant) {
        return tenant.type() == Tenant.Type.cloud;
    }

    private Predicate<Tenant> tenantReadersNotLoggedIn(Duration duration) {
        // returns true if no user has logged in to the tenant after (now - duration)
        return (Tenant tenant) -> {
            var timeLimit = controller().clock().instant().minus(duration);
            return tenant.lastLoginInfo().get(LastLoginInfo.UserLevel.user)
                    .map(instant -> instant.isBefore(timeLimit))
                    .orElse(false);
        };
    }

    private boolean tenantHasTrialPlan(Tenant tenant) {
        var planId = controller().serviceRegistry().billingController().getPlan(tenant.name());
        return "trial".equals(planId.value());
    }

    private boolean tenantHasNonePlan(Tenant tenant) {
        var planId = controller().serviceRegistry().billingController().getPlan(tenant.name());
        return "none".equals(planId.value());
    }

    private boolean tenantIsNotExemptFromExpiry(Tenant tenant) {
        return !extendedTrialTenants.value().contains(tenant.name().value());
    }

    private boolean tenantHasNoDeployments(Tenant tenant) {
        return controller().applications().asList(tenant.name()).stream()
                .flatMap(app -> app.instances().values().stream())
                .mapToLong(instance -> instance.deployments().values().size())
                .sum() == 0;
    }

    private boolean setPlanNone(List<Tenant> tenants) {
        var success = true;
        for (var tenant : tenants) {
            try {
                controller().serviceRegistry().billingController().setPlan(tenant.name(), PlanId.from("none"), false, false);
            } catch (RuntimeException e) {
                log.info("Could not change plan for " + tenant.name() + ": " + e.getMessage());
                success = false;
            }
        }
        return success;
    }

    private boolean tombstoneTenants(List<Tenant> tenants) {
        var success = true;
        for (var tenant : tenants) {
            success &= deleteApplicationsWithNoDeployments(tenant);
            log.fine("Tombstoning empty tenant: " + tenant.name());
            try {
                controller().tenants().delete(tenant.name(), Optional.empty(), false);
            } catch (RuntimeException e) {
                log.info("Could not tombstone tenant " + tenant.name() + ": " + e.getMessage());
                success = false;
            }
        }
        return success;
    }

    private boolean deleteApplicationsWithNoDeployments(Tenant tenant) {
        // this method only removes applications with no active deployments in them
        var success = true;
        for (var application : controller().applications().asList(tenant.name())) {
            try {
                log.fine("Removing empty application: " + application.id());
                controller().applications().deleteApplication(application.id(), Optional.empty());
            } catch (RuntimeException e) {
                log.info("Could not removing application " + application.id() + ": " + e.getMessage());
                success = false;
            }
        }
        return success;
    }
}
