// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.ClusterInfo;
import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.BcpGroupInfo;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;
import com.yahoo.vespa.hosted.provision.applications.Status;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaling;
import com.yahoo.vespa.hosted.provision.autoscale.Load;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

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
    private static final String groupSizeKey = "groupSize";
    private static final String requiredKey = "required";
    private static final String suggestedKey = "suggested";
    private static final String clusterInfoKey = "clusterInfo";
    private static final String bcpDeadlineKey = "bcpDeadline";
    private static final String hostTTLKey = "hostTTL";
    private static final String bcpGroupInfoKey = "bcpGroupInfo";
    private static final String queryRateKey = "queryRateKey";
    private static final String growthRateHeadroomKey = "growthRateHeadroomKey";
    private static final String cpuCostPerQueryKey = "cpuCostPerQueryKey";
    private static final String resourcesKey = "resources";
    private static final String targetKey = "target";
    private static final String nodesKey = "nodes";
    private static final String groupsKey = "groups";
    private static final String nodeResourcesKey = "resources";
    private static final String scalingEventsKey = "scalingEvents";
    private static final String descriptionKey = "description";
    private static final String peakKey = "peak";
    private static final String idealKey = "ideal";
    private static final String metricsKey = "metrics";
    private static final String cpuKey = "cpu";
    private static final String memoryKey = "memory";
    private static final String diskKey = "disk";
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
        toSlime(cluster.groupSize(), clusterObject.setObject(groupSizeKey));
        clusterObject.setBool(requiredKey, cluster.required());
        toSlime(cluster.suggested(), clusterObject.setObject(suggestedKey));
        toSlime(cluster.target(), clusterObject.setObject(targetKey));
        if (! cluster.clusterInfo().isEmpty())
            toSlime(cluster.clusterInfo(), clusterObject.setObject(clusterInfoKey));
        if (! cluster.bcpGroupInfo().isEmpty())
            toSlime(cluster.bcpGroupInfo(), clusterObject.setObject(bcpGroupInfoKey));
        scalingEventsToSlime(cluster.scalingEvents(), clusterObject.setArray(scalingEventsKey));
    }

    private static Cluster clusterFromSlime(String id, Inspector clusterObject) {
        return new Cluster(ClusterSpec.Id.from(id),
                           clusterObject.field(exclusiveKey).asBool(),
                           clusterResourcesFromSlime(clusterObject.field(minResourcesKey)),
                           clusterResourcesFromSlime(clusterObject.field(maxResourcesKey)),
                           intRangeFromSlime(clusterObject.field(groupSizeKey)),
                           clusterObject.field(requiredKey).asBool(),
                           autoscalingFromSlime(clusterObject.field(suggestedKey)),
                           autoscalingFromSlime(clusterObject.field(targetKey)),
                           clusterInfoFromSlime(clusterObject.field(clusterInfoKey)),
                           bcpGroupInfoFromSlime(clusterObject.field(bcpGroupInfoKey)),
                           scalingEventsFromSlime(clusterObject.field(scalingEventsKey)));
    }

    private static void toSlime(Autoscaling autoscaling, Cursor autoscalingObject) {
        autoscalingObject.setString(statusKey, toAutoscalingStatusCode(autoscaling.status()));
        autoscalingObject.setString(descriptionKey, autoscaling.description());
        autoscaling.resources().ifPresent(resources -> toSlime(resources, autoscalingObject.setObject(resourcesKey)));
        autoscalingObject.setLong(atKey, autoscaling.at().toEpochMilli());
        toSlime(autoscaling.peak(), autoscalingObject.setObject(peakKey));
        toSlime(autoscaling.ideal(), autoscalingObject.setObject(idealKey));
        toSlime(autoscaling.metrics(), autoscalingObject.setObject(metricsKey));
    }

    private static void toSlime(ClusterResources resources, Cursor clusterResourcesObject) {
        clusterResourcesObject.setLong(nodesKey, resources.nodes());
        clusterResourcesObject.setLong(groupsKey, resources.groups());
        NodeResourcesSerializer.toSlime(resources.nodeResources(), clusterResourcesObject.setObject(nodeResourcesKey));
    }

    private static Optional<ClusterResources> optionalClusterResourcesFromSlime(Inspector clusterResourcesObject) {
        if ( ! clusterResourcesObject.valid()) return Optional.empty();
        return Optional.of(clusterResourcesFromSlime(clusterResourcesObject));
    }

    private static ClusterResources clusterResourcesFromSlime(Inspector clusterResourcesObject) {
        return new ClusterResources((int)clusterResourcesObject.field(nodesKey).asLong(),
                                    (int)clusterResourcesObject.field(groupsKey).asLong(),
                                    NodeResourcesSerializer.resourcesFromSlime(clusterResourcesObject.field(nodeResourcesKey)));
    }

    private static void toSlime(IntRange range, Cursor rangeObject) {
        range.from().ifPresent(from -> rangeObject.setLong(fromKey, from));
        range.to().ifPresent(from -> rangeObject.setLong(toKey, from));
    }

    private static IntRange intRangeFromSlime(Inspector rangeObject) {
        if ( ! rangeObject.valid()) return IntRange.empty();
        return new IntRange(optionalInt(rangeObject.field(fromKey)), optionalInt(rangeObject.field(toKey)));
    }

    private static void toSlime(Load load, Cursor loadObject) {
        loadObject.setDouble(cpuKey, load.cpu());
        loadObject.setDouble(memoryKey, load.memory());
        loadObject.setDouble(diskKey, load.disk());
    }

    private static Load loadFromSlime(Inspector loadObject) {
        return new Load(loadObject.field(cpuKey).asDouble(),
                        loadObject.field(memoryKey).asDouble(),
                        loadObject.field(diskKey).asDouble());
    }

    private static void toSlime(Autoscaling.Metrics metrics, Cursor metricsObject) {
        metricsObject.setDouble(queryRateKey, metrics.queryRate());
        metricsObject.setDouble(growthRateHeadroomKey, metrics.growthRateHeadroom());
        metricsObject.setDouble(cpuCostPerQueryKey, metrics.cpuCostPerQuery());
    }

    private static Autoscaling.Metrics metricsFromSlime(Inspector metricsObject) {
        return new Autoscaling.Metrics(metricsObject.field(queryRateKey).asDouble(),
                                       metricsObject.field(growthRateHeadroomKey).asDouble(),
                                       metricsObject.field(cpuCostPerQueryKey).asDouble());
    }

    private static Autoscaling autoscalingFromSlime(Inspector autoscalingObject) {
        if ( ! autoscalingObject.valid()) return Autoscaling.empty();

        return new Autoscaling(fromAutoscalingStatusCode(autoscalingObject.field(statusKey).asString()),
                               autoscalingObject.field(descriptionKey).asString(),
                               optionalClusterResourcesFromSlime(autoscalingObject.field(resourcesKey)),
                               Instant.ofEpochMilli(autoscalingObject.field(atKey).asLong()),
                               loadFromSlime(autoscalingObject.field(peakKey)),
                               loadFromSlime(autoscalingObject.field(idealKey)),
                               metricsFromSlime(autoscalingObject.field(metricsKey)));
    }

    private static void toSlime(ClusterInfo clusterInfo, Cursor clusterInfoObject) {
        clusterInfoObject.setLong(bcpDeadlineKey, clusterInfo.bcpDeadline().toMinutes());
        if ( ! clusterInfo.hostTTL().isZero()) clusterInfoObject.setLong(hostTTLKey, clusterInfo.hostTTL().toMillis());
    }

    private static ClusterInfo clusterInfoFromSlime(Inspector clusterInfoObject) {
        if ( ! clusterInfoObject.valid()) return ClusterInfo.empty();
        ClusterInfo.Builder builder = new ClusterInfo.Builder();
        builder.bcpDeadline(Duration.ofMinutes(clusterInfoObject.field(bcpDeadlineKey).asLong()));
        builder.hostTTL(Duration.ofMillis(clusterInfoObject.field(hostTTLKey).asLong()));
        return builder.build();
    }

    private static void toSlime(BcpGroupInfo bcpGroupInfo, Cursor bcpGroupInfoObject) {
        bcpGroupInfoObject.setDouble(queryRateKey, bcpGroupInfo.queryRate());
        bcpGroupInfoObject.setDouble(growthRateHeadroomKey, bcpGroupInfo.growthRateHeadroom());
        bcpGroupInfoObject.setDouble(cpuCostPerQueryKey, bcpGroupInfo.cpuCostPerQuery());
    }

    private static BcpGroupInfo bcpGroupInfoFromSlime(Inspector bcpGroupInfoObject) {
        if ( ! bcpGroupInfoObject.valid()) return BcpGroupInfo.empty();
        return new BcpGroupInfo(bcpGroupInfoObject.field(queryRateKey).asDouble(),
                                bcpGroupInfoObject.field(growthRateHeadroomKey).asDouble(),
                                bcpGroupInfoObject.field(cpuCostPerQueryKey).asDouble());
    }

    private static void scalingEventsToSlime(List<ScalingEvent> scalingEvents, Cursor eventArray) {
        scalingEvents.forEach(event -> toSlime(event, eventArray.addObject()));
    }

    private static List<ScalingEvent> scalingEventsFromSlime(Inspector eventArray) {
        return SlimeUtils.entriesStream(eventArray).map(item -> scalingEventFromSlime(item)).toList();
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

    private static String toAutoscalingStatusCode(Autoscaling.Status status) {
        return switch (status) {
            case unavailable -> "unavailable";
            case waiting -> "waiting";
            case ideal -> "ideal";
            case insufficient -> "insufficient";
            case rescaling -> "rescaling";
        };
    }

    private static Autoscaling.Status fromAutoscalingStatusCode(String code) {
        return switch (code) {
            case "" -> Autoscaling.Status.unavailable;
            case "unavailable" -> Autoscaling.Status.unavailable;
            case "waiting" -> Autoscaling.Status.waiting;
            case "ideal" -> Autoscaling.Status.ideal;
            case "insufficient" -> Autoscaling.Status.insufficient;
            case "rescaling" -> Autoscaling.Status.rescaling;
            default -> throw new IllegalArgumentException("Unknown autoscaling status '" + code + "'");
        };
    }

    private static Optional<Instant> optionalInstant(Inspector inspector) {
        return inspector.valid() ? Optional.of(Instant.ofEpochMilli(inspector.asLong())) : Optional.empty();
    }

    private static OptionalInt optionalInt(Inspector inspector) {
        return inspector.valid() ? OptionalInt.of((int)inspector.asLong()) : OptionalInt.empty();
    }

}
