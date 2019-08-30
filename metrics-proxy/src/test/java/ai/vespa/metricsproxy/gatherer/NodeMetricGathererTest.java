package ai.vespa.metricsproxy.gatherer;

import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.metric.HealthMetric;
import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import ai.vespa.metricsproxy.service.VespaService;
import ai.vespa.metricsproxy.service.VespaServices;
import org.junit.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author olaa
 */
public class NodeMetricGathererTest {

    @Test
    public void gatherMetrics() throws Exception {

        MetricsManager metricsManager = mock(MetricsManager.class);
        VespaServices vespaServices = mock(VespaServices.class);
        ApplicationDimensions applicationDimensions = mock(ApplicationDimensions.class);
        NodeDimensions nodeDimensions = mock(NodeDimensions.class);
        NodeMetricGatherer.FileWrapper fileWrapper = mock(NodeMetricGatherer.FileWrapper.class);
        List<VespaService> mockedVespaServices = mockedVespaServices();

        NodeMetricGatherer nodeMetricGatherer = new NodeMetricGatherer(metricsManager, vespaServices, applicationDimensions, nodeDimensions, fileWrapper);
        when(fileWrapper.walkTree(any())).thenReturn(List.of(Path.of("")).stream());
        when(fileWrapper.getLastModifiedTime(any())).thenReturn(Instant.ofEpochMilli(0));
        when(fileWrapper.readAllLines(any())).thenReturn(List.of("123 456"));
        when(vespaServices.getVespaServices()).thenReturn(mockedVespaServices);
        when(applicationDimensions.getDimensions()).thenReturn(Collections.emptyMap());
        when(nodeDimensions.getDimensions()).thenReturn(Collections.emptyMap());

        List<MetricsPacket> packets = nodeMetricGatherer.gatherMetrics();
        assertEquals(5, packets.size());
        Map<DimensionId, String> serviceHealthDimensions = Map.of(DimensionId.toDimensionId("instance"), "instance", DimensionId.toDimensionId("metrictype"), "health");
        List<MetricsPacket> expectedPackets = List.of(
            metricsPacket(0, "system-coredumps-processing", Collections.emptyList(), Collections.emptyMap()),
            metricsPacket(0, "host_life", List.of(new Metric("uptime", 123), new Metric("alive", 1)), Collections.emptyMap()),
            metricsPacket(0, "service1", Collections.emptyList(), serviceHealthDimensions),
            metricsPacket(0, "service2", Collections.emptyList(), serviceHealthDimensions),
            metricsPacket(1, "service3", Collections.emptyList(), serviceHealthDimensions)
        );

        assertEqualMetricPackets(expectedPackets, packets);
    }

    private void assertEqualMetricPackets(List<MetricsPacket> expectedPackets, List<MetricsPacket> actualPackets) {
        assertEquals(expectedPackets.size(), actualPackets.size());
        expectedPackets.stream()
                .forEach(expectedPacket ->
                    actualPackets.stream()
                            .filter(packet -> packet.service.equals(expectedPacket.service))
                            .forEach(actualPacket -> {
                                assertEquals(expectedPacket.statusMessage, actualPacket.statusMessage);
                                assertEquals(expectedPacket.statusCode, actualPacket.statusCode);
                                assertEquals(expectedPacket.metrics(), actualPacket.metrics());
                                assertEquals(expectedPacket.dimensions(), actualPacket.dimensions());
                            })
                );
    }

    private List<VespaService> mockedVespaServices() {

        HealthMetric healthy = HealthMetric.get("OK", "");
        HealthMetric unhealthy = HealthMetric.get("down", "");

        VespaService service1 = mock(VespaService.class);
        when(service1.getInstanceName()).thenReturn("instance");
        when(service1.getMonitoringName()).thenReturn("service1");
        when(service1.getHealth()).thenReturn(healthy);

        VespaService service2 = mock(VespaService.class);
        when(service2.getInstanceName()).thenReturn("instance");
        when(service2.getMonitoringName()).thenReturn("service2");
        when(service2.getHealth()).thenReturn(healthy);


        VespaService service3 = mock(VespaService.class);
        when(service3.getInstanceName()).thenReturn("instance");
        when(service3.getMonitoringName()).thenReturn("service3");
        when(service3.getHealth()).thenReturn(unhealthy);

        return List.of(service1, service2, service3);

    }

    private MetricsPacket metricsPacket(int statusCode, String service,
                                        Collection<Metric> metrics, Map<DimensionId, String> dimensions) {
        return new MetricsPacket.Builder(ServiceId.toServiceId(service))
                .putDimensions(dimensions)
                .putMetrics(metrics)
                .statusCode(statusCode)
                .build();
    }
}