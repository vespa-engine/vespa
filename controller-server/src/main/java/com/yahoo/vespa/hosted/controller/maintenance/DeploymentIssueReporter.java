// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.Tenant;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.Contacts;
import com.yahoo.vespa.hosted.controller.api.integration.Contacts.UserContact;
import com.yahoo.vespa.hosted.controller.api.integration.Issues;
import com.yahoo.vespa.hosted.controller.api.integration.Issues.Classification;
import com.yahoo.vespa.hosted.controller.api.integration.Issues.Issue;
import com.yahoo.vespa.hosted.controller.api.integration.Issues.IssueInfo;
import com.yahoo.vespa.hosted.controller.api.integration.Properties;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.api.integration.Contacts.Category.admin;
import static com.yahoo.vespa.hosted.controller.api.integration.Issues.IssueInfo.Status.done;

/**
 * Maintenance job which creates Jira issues for tenants when they have jobs which fails continuously 
 * and escalates issues which are not handled.
 *
 * @author jvenstad
 */
public class DeploymentIssueReporter extends Maintainer {

    static final Duration maxFailureAge = Duration.ofDays(2);
    static final Duration maxInactivityAge = Duration.ofDays(4);
    static final String deploymentFailureLabel = "vespaDeploymentFailure";
    static final Classification vespaOps = new Classification("VESPA", "Services", deploymentFailureLabel);
    static final UserContact terminalUser = new UserContact("frodelu", "Frode Lundgren", admin);

    private final Contacts contacts;
    private final Properties properties;
    private final Issues issues;

    DeploymentIssueReporter(Controller controller, Contacts contacts, Properties properties, Issues issues, 
                            Duration maintenanceInterval, JobControl jobControl) {
        super(controller, maintenanceInterval, jobControl);
        this.contacts = contacts;
        this.properties = properties;
        this.issues = issues;
    }

    @Override
    protected void maintain() {
        maintainDeploymentIssues(controller().applications().asList());
        escalateInactiveDeploymentIssues(controller().applications().asList());
    }

    /**
     * File issues for applications which have failed deployment for longer than @maxFailureAge
     * and store the issue id for the filed issues. Also, clear the @issueIds of applications
     * where deployment has not failed for this amount of time.
     */
    private void maintainDeploymentIssues(List<Application> applications) {
        Collection<Application> failingApplications = new ArrayList<>();
        for (Application application : applications)
            if (failingSinceBefore(application.deploymentJobs(), controller().clock().instant().minus(maxFailureAge)))
                failingApplications.add(application);
            else
                controller().applications().setJiraIssueId(application.id(), Optional.empty());

        // TODO: Do this when version.confidence is BROKEN instead?
        if (failingApplications.size() > 0.2 * applications.size()) {
            fileOrUpdate(manyFailingDeploymentsIssueFrom(failingApplications)); // Problems with Vespa is the most likely cause when so many deployments fail.
        }
        else {
            for (Application application : failingApplications) {
                Issue deploymentIssue = deploymentIssueFrom(application);
                Classification applicationOwner = null;
                try {
                    applicationOwner = jiraClassificationOf(ownerOf(application));
                    fileFor(application, deploymentIssue.with(applicationOwner));
                }
                catch (RuntimeException e) { // Catch errors due to inconsistent or missing data in Sherpa, OpsDB, JIRA, and send to ourselves.
                    Pattern componentError = Pattern.compile(".*Component name '.*' is not valid.*", Pattern.DOTALL);
                    if (componentError.matcher(e.getMessage()).matches()) // Several properties seem to list invalid components, in which case we simply ignore this.
                        fileFor(application, deploymentIssue.with(applicationOwner.withComponent(null)));
                    else
                        fileFor(application, deploymentIssue.append(e.getMessage() + "\n\nAddressee:\n" + applicationOwner));
                }
            }
        }
    }

    /** Returns whether @deploymentJobs has a job which has been failing since before @failureThreshold or not. */
    private boolean failingSinceBefore(DeploymentJobs deploymentJobs, Instant failureThreshold) {
        return deploymentJobs.hasFailures() && deploymentJobs.failingSince().isBefore(failureThreshold);
    }

    private Tenant ownerOf(Application application) {
        return controller().tenants().tenant(new TenantId(application.id().tenant().value())).get();
    }

    /** Use the @propertyId of @tenant, if present, to look up JIRA information in OpsDB. */
    private Classification jiraClassificationOf(Tenant tenant) {
        Long propertyId = tenant.getPropertyId().map(PropertyId::value).orElseThrow(() ->
                new NoSuchElementException("No property id is listed for " + tenant));

        Classification classification = properties.classificationFor(propertyId).orElseThrow(() ->
                new NoSuchElementException("No property was found with id " + propertyId));

        return classification.withLabel(deploymentFailureLabel);
    }

    /** File @issue for @application, if @application doesn't already have an @Issue associated with it. */
    private void fileFor(Application application, Issue issue) {
        Optional<String> ourIssueId = application.deploymentJobs().jiraIssueId()
                .filter(jiraIssueId -> issues.fetch(jiraIssueId).status() != done);

        if ( ! ourIssueId.isPresent())
            controller().applications().setJiraIssueId(application.id(), Optional.of(issues.file(issue)));
    }

    /** File @issue, or update a JIRA issue representing the same issue. */
    private void fileOrUpdate(Issue issue) {
        Optional<String> jiraIssueId = issues.fetchSimilarTo(issue)
                .stream().findFirst().map(Issues.IssueInfo::id);

        if (jiraIssueId.isPresent())
            issues.update(jiraIssueId.get(), issue.description());
        else
            issues.file(issue);
    }

    /** Escalate JIRA issues for which there has been no activity for a set amount of time. */
    private void escalateInactiveDeploymentIssues(List<Application> applications) {
        applications.forEach(application ->
                application.deploymentJobs().jiraIssueId().ifPresent(jiraIssueId -> {
                    Issues.IssueInfo issueInfo = issues.fetch(jiraIssueId);
                    if (issueInfo.updated().isBefore(controller().clock().instant().minus(maxInactivityAge)))
                        escalateAndComment(issueInfo, application);
                }));
    }

    /** Reassign the JIRA issue for @application one step up in the OpsDb escalation chain, and add an explanatory comment to it. */
    private void escalateAndComment(IssueInfo issueInfo, Application application) {
        Optional<String> assignee = issueInfo.assignee();
        if (assignee.isPresent()) {
            if (assignee.get().equals(terminalUser.username())) return;
            issues.addWatcher(issueInfo.id(), assignee.get());
        }

        Long propertyId = ownerOf(application).getPropertyId().get().value();

        UserContact escalationTarget = contacts.escalationTargetFor(propertyId, assignee.orElse("no one"));
        if (escalationTarget.is(assignee.orElse("no one")))
            escalationTarget = terminalUser;

        String comment = deploymentIssueEscalationComment(application, propertyId, assignee.orElse("anyone"));

        issues.comment(issueInfo.id(), comment);
        issues.reassign(issueInfo.id(), escalationTarget.username());
    }

    Issue deploymentIssueFrom(Application application) {
        return new Issue(deploymentIssueSummary(application), deploymentIssueDescription(application))
                .with(vespaOps);
    }

    Issue manyFailingDeploymentsIssueFrom(Collection<Application> applications) {
        return new Issue(
                "More than 20% of Hosted Vespa deployments are failing",
                applications.stream()
                        .map(application -> "[" + application.id().toShortString() + "|" + toUrl(application.id()) + "]")
                        .collect(Collectors.joining("\n")),
                vespaOps);
    }

    // TODO: Use the method of the same name in ApplicationId
    private static String toShortString(ApplicationId id) {
        return id.tenant().value() + "." + id.application().value() +
                ( id.instance().isDefault() ? "" : "." + id.instance().value() );
    }

    private String toUrl(ApplicationId applicationId) {
        return controller().zoneRegistry().getDashboardUri().resolve("/apps" +
               "/tenant/" + applicationId.tenant().value() +
               "/application/" + applicationId.application().value()).toString();
    }

    private String toOpsDbUrl(long propertyId) {
        return contacts.contactsUri(propertyId).toString();

    }

    /** Returns the summary text what will be assigned to a new issue */
    private static String deploymentIssueSummary(Application application) {
        return "[" + toShortString(application.id()) + "] Action required: Repair deployment";
    }

    /** Returns the description text what will be assigned to a new issue */
    private String deploymentIssueDescription(Application application) {
        return "Deployment jobs of the Vespa application " +
                "[" + toShortString(application.id()) + "|" + toUrl(application.id()) + "] have been failing " +
                "continuously for over 48 hours. This blocks any change to this application from being deployed " +
                "and will also block global rollout of new Vespa versions for everybody.\n\n" +
                "Please assign your highest priority to fixing this. If you need support, request it using " +
                "[yo/vespa-support|http://yo/vespa-support]. " +
                "If this application is not in use, please re-assign this issue to project \"VESPA\" " +
                "with component \"Services\", and ask for the application to be removed.\n\n" +
                "If we do not get a response on this issue, we will auto-escalate it.";
    }

    /** Returns the comment text that what will be added to an issue each time it is escalated */
    private String deploymentIssueEscalationComment(Application application, long propertyId, String priorAssignee) {
        return "This issue tracks the failing deployment of Vespa application " +
                "[" + toShortString(application.id()) + "|" + toUrl(application.id()) + "]. " +
                "Since we have not received a response from " + priorAssignee +
                ", we are escalating to you, " +
                "based on [your OpsDb information|" + toOpsDbUrl(propertyId) + "]. " +
                "Please acknowledge this issue and assign somebody to " +
                "fix it as soon as possible.\n\n" +
                "If we do not receive a response we will keep auto-escalating this issue. " +
                "If we run out of escalation options for your OpsDb property, we will assume this application " +
                "is not managed by anyone and DELETE it. In the meantime, this issue will block global deployment " +
                "of Vespa for the entire company.";
    }

}
