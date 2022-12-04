// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.AutoscalingStatus;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;
import com.yahoo.vespa.hosted.provision.applications.Status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Application JSON serializer
 *
 * @author bratseth
 */
public class ApplicationSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String idKey = "id";

    private static final String statusKey = "status";
    private static final String currentReadShareKey = "currentReadShare";
    private static final String maxReadShareKey = "maxReadShare";

    private static final String clustersKey = "clusters";
    private static final String exclusiveKey = "exclusive";
    private static final String minResourcesKey = "min";
    private static final String maxResourcesKey = "max";
    private static final String requiredKey = "required";
    private static final String suggestedKey = "suggested";
    private static final String resourcesKey = "resources";
    private static final String targetResourcesKey = "target";
    private static final String nodesKey = "nodes";
    private static final String groupsKey = "groups";
    private static final String nodeResourcesKey = "resources";
    private static final String scalingEventsKey = "scalingEvents";
    private static final String autoscalingStatusKey = "autoscalingStatus";
    private static final String autoscalingStatusObjectKey = "autoscalingStatusObject";
    private static final String descriptionKey = "description";
    private static final String fromKey = "from";
    private static final String toKey = "to";
    private static final String generationKey = "generation";
    private static final String atKey = "at";
    private static final String completionKey = "completion";

    public static byte[] toJson(Application application) {
        Slime slime = new Slime();
        toSlime(application, slime.setObject());
        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Application fromJson(byte[] data) {
        return applicationFromSlime(SlimeUtils.jsonToSlime(data).get());
    }

    // ---------------------------------------------------------------------------------------

    private static void toSlime(Application application, Cursor object) {
        object.setString(idKey, application.id().serializedForm());
        toSlime(application.status(), object.setObject(statusKey));
        clustersToSlime(application.clusters().values(), object.setObject(clustersKey));
    }

    private static Application applicationFromSlime(Inspector applicationObject) {
        ApplicationId id = ApplicationId.fromSerializedForm(applicationObject.field(idKey).asString());
        return new Application(id,
                               statusFromSlime(applicationObject.field(statusKey)),
                               clustersFromSlime(applicationObject.field(clustersKey)));
    }

    private static void toSlime(Status status, Cursor statusObject) {
        statusObject.setDouble(currentReadShareKey, status.currentReadShare());
        statusObject.setDouble(maxReadShareKey, status.maxReadShare());
    }

    private static Status statusFromSlime(Inspector statusObject) {
        return new Status(statusObject.field(currentReadShareKey).asDouble(),
                          statusObject.field(maxReadShareKey).asDouble());
    }

    private static void clustersToSlime(Collection<Cluster> clusters, Cursor clustersObject) {
        clusters.forEach(cluster -> toSlime(cluster, clustersObject.setObject(cluster.id().value())));
    }

    private static Collection<Cluster> clustersFromSlime(Inspector clustersObject) {
        List<Cluster> clusters = new ArrayList<>();
        clustersObject.traverse((ObjectTraverser)(id, clusterObject) -> clusters.add(clusterFromSlime(id, clusterObject)));
        return clusters;
    }

    private static void toSlime(Cluster cluster, Cursor clusterObject) {
        clusterObject.setBool(exclusiveKey, cluster.exclusive());
        toSlime(cluster.minResources(), clusterObject.setObject(minResourcesKey));
        toSlime(cluster.maxResources(), clusterObject.setObject(maxResourcesKey));
        clusterObject.setBool(requiredKey, cluster.required());
        cluster.suggestedResources().ifPresent(suggested -> toSlime(suggested, clusterObject.setObject(suggestedKey)));
        cluster.targetResources().ifPresent(target -> toSlime(target, clusterObject.setObject(targetResourcesKey)));
        scalingEventsToSlime(cluster.scalingEvents(), clusterObject.setArray(scalingEventsKey));
        clusterObject.setString(autoscalingStatusKey, cluster.autoscalingStatus().description()); // TODO: Remove after June 2021
        toSlime(cluster.autoscalingStatus(), clusterObject.setObject(autoscalingStatusObjectKey));
    }

    private static Cluster clusterFromSlime(String id, Inspector clusterObject) {
        return new Cluster(ClusterSpec.Id.from(id),
                           clusterObject.field(exclusiveKey).asBool(),
                           clusterResourcesFromSlime(clusterObject.field(minResourcesKey)),
                           clusterResourcesFromSlime(clusterObject.field(maxResourcesKey)),
                           clusterObject.field(requiredKey).asBool(),
                           optionalSuggestionFromSlime(clusterObject.field(suggestedKey)),
                           optionalClusterResourcesFromSlime(clusterObject.field(targetResourcesKey)),
                           scalingEventsFromSlime(clusterObject.field(scalingEventsKey)),
                           autoscalingStatusFromSlime(clusterObject.field(autoscalingStatusObjectKey), clusterObject));
    }

    private static void toSlime(Cluster.Suggestion suggestion, Cursor suggestionObject) {
        toSlime(suggestion.resources(), suggestionObject.setObject(resourcesKey));
        suggestionObject.setLong(atKey, suggestion.at().toEpochMilli());
    }

    private static void toSlime(ClusterResources resources, Cursor clusterResourcesObject) {
        clusterResourcesObject.setLong(nodesKey, resources.nodes());
        clusterResourcesObject.setLong(groupsKey, resources.groups());
        NodeResourcesSerializer.toSlime(resources.nodeResources(), clusterResourcesObject.setObject(nodeResourcesKey));
    }

    private static ClusterResources clusterResourcesFromSlime(Inspector clusterResourcesObject) {
        return new ClusterResources((int)clusterResourcesObject.field(nodesKey).asLong(),
                                    (int)clusterResourcesObject.field(groupsKey).asLong(),
                                    NodeResourcesSerializer.resourcesFromSlime(clusterResourcesObject.field(nodeResourcesKey)));
    }

    private static Optional<Cluster.Suggestion> optionalSuggestionFromSlime(Inspector suggestionObject) {
        if ( ! suggestionObject.valid()) return Optional.empty();

        if (suggestionObject.field(nodesKey).valid()) // TODO: Remove this line and the next after January 2021
            return Optional.of(new Cluster.Suggestion(clusterResourcesFromSlime(suggestionObject),  Instant.EPOCH));

        return Optional.of(new Cluster.Suggestion(clusterResourcesFromSlime(suggestionObject.field(resourcesKey)),
                                                  Instant.ofEpochMilli(suggestionObject.field(atKey).asLong())));
    }

    private static Optional<ClusterResources> optionalClusterResourcesFromSlime(Inspector clusterResourcesObject) {
        return clusterResourcesObject.valid() ? Optional.of(clusterResourcesFromSlime(clusterResourcesObject))
                                              : Optional.empty();
    }

    private static void scalingEventsToSlime(List<ScalingEvent> scalingEvents, Cursor eventArray) {
        scalingEvents.forEach(event -> toSlime(event, eventArray.addObject()));
    }

    private static List<ScalingEvent> scalingEventsFromSlime(Inspector eventArray) {
        return SlimeUtils.entriesStream(eventArray).map(item -> scalingEventFromSlime(item)).collect(Collectors.toList());
    }

    private static void toSlime(ScalingEvent event, Cursor object) {
        toSlime(event.from(), object.setObject(fromKey));
        toSlime(event.to(), object.setObject(toKey));
        object.setLong(generationKey, event.generation());
        object.setLong(atKey, event.at().toEpochMilli());
        event.completion().ifPresent(completion -> object.setLong(completionKey, completion.toEpochMilli()));
    }

    private static ScalingEvent scalingEventFromSlime(Inspector inspector) {
        return new ScalingEvent(clusterResourcesFromSlime(inspector.field(fromKey)),
                                clusterResourcesFromSlime(inspector.field(toKey)),
                                inspector.field(generationKey).asLong(),
                                Instant.ofEpochMilli(inspector.field(atKey).asLong()),
                                optionalInstant(inspector.field(completionKey)));
    }

    private static void toSlime(AutoscalingStatus status, Cursor object) {
        object.setString(statusKey, toAutoscalingStatusCode(status.status()));
        object.setString(descriptionKey, status.description());
    }

    private static AutoscalingStatus autoscalingStatusFromSlime(Inspector object, Inspector parent) {
        // TODO: Remove this clause after June 2021
        if ( ! object.valid()) return new AutoscalingStatus(AutoscalingStatus.Status.unavailable,
                                                            parent.field(autoscalingStatusKey).asString());

        return new AutoscalingStatus(fromAutoscalingStatusCode(object.field(statusKey).asString()),
                                     object.field(descriptionKey).asString());
    }

    private static String toAutoscalingStatusCode(AutoscalingStatus.Status status) {
        switch (status) {
            case unavailable : return "unavailable";
            case waiting : return "waiting";
            case ideal : return "ideal";
            case insufficient : return "insufficient";
            case rescaling : return "rescaling";
            default : throw new IllegalArgumentException("Unknown autoscaling status " + status);
        }
    }

    private static AutoscalingStatus.Status fromAutoscalingStatusCode(String code) {
        switch (code) {
            case "unavailable" : return AutoscalingStatus.Status.unavailable;
            case "waiting" : return AutoscalingStatus.Status.waiting;
            case "ideal" : return AutoscalingStatus.Status.ideal;
            case "insufficient" : return AutoscalingStatus.Status.insufficient;
            case "rescaling" : return AutoscalingStatus.Status.rescaling;
            default : throw new IllegalArgumentException("Unknown autoscaling status '" + code + "'");
        }
    }

    private static Optional<Instant> optionalInstant(Inspector inspector) {
        return inspector.valid() ? Optional.of(Instant.ofEpochMilli(inspector.asLong())) : Optional.empty();
    }

}
