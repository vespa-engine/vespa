// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.notification.Notification;
import com.yahoo.vespa.hosted.controller.notification.NotificationSource;

import java.util.List;
import java.util.stream.Collectors;

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
    private static final String messagesField = "messages";
    private static final String applicationField = "application";
    private static final String instanceField = "instance";
    private static final String zoneField = "zone";
    private static final String clusterIdField = "clusterId";
    private static final String jobTypeField = "jobId";
    private static final String runNumberField = "runNumber";

    public static Slime toSlime(List<Notification> notifications) {
        Slime slime = new Slime();
        Cursor notificationsArray = slime.setObject().setArray(notificationsFieldName);

        for (Notification notification : notifications) {
            Cursor notificationObject = notificationsArray.addObject();
            notificationObject.setLong(atFieldName, notification.at().toEpochMilli());
            notificationObject.setString(typeField, asString(notification.type()));
            notificationObject.setString(levelField, asString(notification.level()));
            Cursor messagesArray = notificationObject.setArray(messagesField);
            notification.messages().forEach(messagesArray::addString);

            notification.source().application().ifPresent(application -> notificationObject.setString(applicationField, application.value()));
            notification.source().instance().ifPresent(instance -> notificationObject.setString(instanceField, instance.value()));
            notification.source().zoneId().ifPresent(zoneId -> notificationObject.setString(zoneField, zoneId.value()));
            notification.source().clusterId().ifPresent(clusterId -> notificationObject.setString(clusterIdField, clusterId.value()));
            notification.source().jobType().ifPresent(jobType -> notificationObject.setString(jobTypeField, jobType.jobName()));
            notification.source().runNumber().ifPresent(runNumber -> notificationObject.setLong(runNumberField, runNumber));
        }

        return slime;
    }

    public static List<Notification> fromSlime(TenantName tenantName, Slime slime) {
        return SlimeUtils.entriesStream(slime.get().field(notificationsFieldName))
                .map(inspector -> fromInspector(tenantName, inspector))
                .collect(Collectors.toUnmodifiableList());
    }

    private static Notification fromInspector(TenantName tenantName, Inspector inspector) {
        return new Notification(
               Serializers.instant(inspector.field(atFieldName)),
               typeFrom(inspector.field(typeField)),
               levelFrom(inspector.field(levelField)),
               new NotificationSource(
                       tenantName,
                       Serializers.optionalString(inspector.field(applicationField)).map(ApplicationName::from),
                       Serializers.optionalString(inspector.field(instanceField)).map(InstanceName::from),
                       Serializers.optionalString(inspector.field(zoneField)).map(ZoneId::from),
                       Serializers.optionalString(inspector.field(clusterIdField)).map(ClusterSpec.Id::from),
                       Serializers.optionalString(inspector.field(jobTypeField)).map(JobType::fromJobName),
                       Serializers.optionalLong(inspector.field(runNumberField))),
               SlimeUtils.entriesStream(inspector.field(messagesField)).map(Inspector::asString).collect(Collectors.toUnmodifiableList()));
    }
    
    private static String asString(Notification.Type type) {
        switch (type) {
            case applicationPackage: return "applicationPackage";
            case deployment: return "deployment";
            case feedBlock: return "feedBlock";
            default: throw new IllegalArgumentException("No serialization defined for notification type " + type);
        }
    }

    private static Notification.Type typeFrom(Inspector field) {
        switch (field.asString()) {
            case "applicationPackage": return Notification.Type.applicationPackage;
            case "deployment": return Notification.Type.deployment;
            case "feedBlock": return Notification.Type.feedBlock;
            default: throw new IllegalArgumentException("Unknown serialized notification type value '" + field.asString() + "'");
        }
    }

    private static String asString(Notification.Level level) {
        switch (level) {
            case warning: return "warning";
            case error: return "error";
            default: throw new IllegalArgumentException("No serialization defined for notification level " + level);
        }
    }

    private static Notification.Level levelFrom(Inspector field) {
        switch (field.asString()) {
            case "warning": return Notification.Level.warning;
            case "error": return Notification.Level.error;
            default: throw new IllegalArgumentException("Unknown serialized notification level value '" + field.asString() + "'");
        }
    }
}
