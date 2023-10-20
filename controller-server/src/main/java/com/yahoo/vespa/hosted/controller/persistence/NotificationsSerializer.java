// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.notification.Notification;
import com.yahoo.vespa.hosted.controller.notification.NotificationSource;

import java.util.List;
import java.util.Optional;

/**
 * (de)serializes notifications for a tenant
 *
 * @author freva
 */
public class NotificationsSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String notificationsFieldName = "notifications";
    private static final String atFieldName = "at";
    private static final String typeField = "type";
    private static final String levelField = "level";
    private static final String titleField = "title";
    private static final String messagesField = "messages";
    private static final String applicationField = "application";
    private static final String instanceField = "instance";
    private static final String zoneField = "zone";
    private static final String clusterIdField = "clusterId";
    private static final String jobTypeField = "jobId";
    private static final String runNumberField = "runNumber";

    public Slime toSlime(List<Notification> notifications) {
        Slime slime = new Slime();
        Cursor notificationsArray = slime.setObject().setArray(notificationsFieldName);

        for (Notification notification : notifications) {
            Cursor notificationObject = notificationsArray.addObject();
            notificationObject.setLong(atFieldName, notification.at().toEpochMilli());
            notificationObject.setString(typeField, asString(notification.type()));
            notificationObject.setString(levelField, asString(notification.level()));
            notificationObject.setString(titleField, notification.title());
            Cursor messagesArray = notificationObject.setArray(messagesField);
            notification.messages().forEach(messagesArray::addString);

            notification.source().application().ifPresent(application -> notificationObject.setString(applicationField, application.value()));
            notification.source().instance().ifPresent(instance -> notificationObject.setString(instanceField, instance.value()));
            notification.source().zoneId().ifPresent(zoneId -> notificationObject.setString(zoneField, zoneId.value()));
            notification.source().clusterId().ifPresent(clusterId -> notificationObject.setString(clusterIdField, clusterId.value()));
            notification.source().jobType().ifPresent(jobType -> notificationObject.setString(jobTypeField, jobType.serialized()));
            notification.source().runNumber().ifPresent(runNumber -> notificationObject.setLong(runNumberField, runNumber));

            notification.mailContent().ifPresent(mc -> {
                notificationObject.setString("mail-template", mc.template());
                mc.subject().ifPresent(s -> notificationObject.setString("mail-subject", s));
                var mailParamsCursor = notificationObject.setObject("mail-params");
                mc.values().forEach((key, value) -> {
                    if (value instanceof String str) {
                        mailParamsCursor.setString(key, str);
                    } else if (value instanceof List<?> l) {
                        var array = mailParamsCursor.setArray(key);
                        l.forEach(elem -> array.addString((String) elem));
                    } else {
                        throw new ClassCastException("Unsupported param type: " + value.getClass());
                    }
                });
            });
        }

        return slime;
    }

    public List<Notification> fromSlime(TenantName tenantName, Slime slime) {
        return SlimeUtils.entriesStream(slime.get().field(notificationsFieldName))
                         .filter(inspector -> { // TODO: remove in summer.
                             if (!inspector.field(jobTypeField).valid()) return true;
                             try {
                                 JobType.ofSerialized(inspector.field(jobTypeField).asString());
                                 return true;
                             } catch (RuntimeException e) {
                                 return false;
                             }
                         })
                         .map(inspector -> fromInspector(tenantName, inspector)).toList();
    }

    private Notification fromInspector(TenantName tenantName, Inspector inspector) {
        return new Notification(
                SlimeUtils.instant(inspector.field(atFieldName)),
                typeFrom(inspector.field(typeField)),
                levelFrom(inspector.field(levelField)),
                new NotificationSource(
                        tenantName,
                        SlimeUtils.optionalString(inspector.field(applicationField)).map(ApplicationName::from),
                        SlimeUtils.optionalString(inspector.field(instanceField)).map(InstanceName::from),
                        SlimeUtils.optionalString(inspector.field(zoneField)).map(ZoneId::from),
                        SlimeUtils.optionalString(inspector.field(clusterIdField)).map(ClusterSpec.Id::from),
                        SlimeUtils.optionalString(inspector.field(jobTypeField)).map(jobName -> JobType.ofSerialized(jobName)),
                        SlimeUtils.optionalLong(inspector.field(runNumberField))),
                SlimeUtils.optionalString(inspector.field(titleField)).orElse(""),
                SlimeUtils.entriesStream(inspector.field(messagesField)).map(Inspector::asString).toList(),
                mailContentFrom(inspector));
    }

    private Optional<Notification.MailContent> mailContentFrom(final Inspector inspector) {
        return SlimeUtils.optionalString(inspector.field("mail-template")).map(template -> {
            var builder = Notification.MailContent.fromTemplate(template);
            SlimeUtils.optionalString(inspector.field("mail-subject")).ifPresent(builder::subject);
            var paramsCursor = inspector.field("mail-params");
            inspector.field("mail-params").traverse((ObjectTraverser) (name, insp) -> {
                switch (insp.type()) {
                    case STRING -> builder.with(name, insp.asString());
                    case ARRAY -> builder.with(name, SlimeUtils.entriesStream(insp).map(Inspector::asString).toList());
                    default -> throw new IllegalArgumentException("Unsupported param type: " + insp.type());
                }
            });
            return builder.build();
        });
    }
    
    private static String asString(Notification.Type type) {
        return switch (type) {
            case applicationPackage -> "applicationPackage";
            case submission -> "submission";
            case testPackage -> "testPackage";
            case deployment -> "deployment";
            case feedBlock -> "feedBlock";
            case reindex -> "reindex";
            case account -> "account";
        };
    }

    private static Notification.Type typeFrom(Inspector field) {
        return switch (field.asString()) {
            case "applicationPackage" -> Notification.Type.applicationPackage;
            case "submission" -> Notification.Type.submission;
            case "testPackage" -> Notification.Type.testPackage;
            case "deployment" -> Notification.Type.deployment;
            case "feedBlock" -> Notification.Type.feedBlock;
            case "reindex" -> Notification.Type.reindex;
            case "account" -> Notification.Type.account;
            default -> throw new IllegalArgumentException("Unknown serialized notification type value '" + field.asString() + "'");
        };
    }

    private static String asString(Notification.Level level) {
        return switch (level) {
            case info -> "info";
            case warning -> "warning";
            case error -> "error";
        };
    }

    private static Notification.Level levelFrom(Inspector field) {
        return switch (field.asString()) {
            case "info" -> Notification.Level.info;
            case "warning" -> Notification.Level.warning;
            case "error" -> Notification.Level.error;
            default -> throw new IllegalArgumentException("Unknown serialized notification level value '" + field.asString() + "'");
        };
    }

}
