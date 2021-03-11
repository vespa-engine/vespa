// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.rpc;

import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.service.VespaService;
import ai.vespa.metricsproxy.service.VespaServices;
import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;

import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil.toMetricsPackets;
import static ai.vespa.metricsproxy.metric.model.json.YamasJsonUtil.toYamasArray;
import static com.yahoo.collections.CollectionUtil.mkString;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Rpc server for the metrics proxy.
 *
 * When a new object is created after reconfiguration, it will claim ownership of the methods
 * in the given {@link RpcConnector}. This is ok because at the time this component is created,
 * all components it depends on are already created.
 *
 * @author gjoranv
 */
public class RpcServer {

    private static final Logger log = Logger.getLogger(RpcServer.class.getName());

    private static final int LOG_SPENT_TIME_LIMIT = 10 * 1000; // ms. same as default client RPC timeout used in rpc_invoke

    private final VespaServices vespaServices;
    private final MetricsManager metricsManager;

    public RpcServer(RpcConnector connector, VespaServices vespaServices, MetricsManager metricsManager) {
        this.vespaServices = vespaServices;
        this.metricsManager = metricsManager;
        addMethods(connector);
        log.log(FINE, "RPC server created");
    }

    private void addMethods(RpcConnector connector) {
        // Add/replace this method first to increase likelihood of getting extra metrics and global dimensions
        connector.addMethod(
                new Method("setExtraMetrics", "s", "", this::setExtraMetrics)
                        .methodDesc("Set extra metrics that will be added to output from getMetricsForYamas.")
                        .paramDesc(0, "metricsJson", "The metrics in json format"));

        connector.addMethod(
                new Method("purgeExtraMetrics", "", "", this::purgeExtraMetrics)
                        .methodDesc("Purge metrics and dimensions populated by setExtraMetrics"));

        connector.addMethod(
                new Method("getMetricsById", "s", "s", this::getMetricsById)
                        .methodDesc("Get Vespa metrics for the service with the given Id")
                        .paramDesc(0, "id", "The id of the service")
                        .returnDesc(0, "ret", "Vespa metrics"));

        connector.addMethod(
                new Method("getServices", "", "s", this::getServices)
                        .methodDesc("Get Vespa services monitored by this metrics proxy")
                        .returnDesc(0, "ret", "Vespa metrics"));

        connector.addMethod(
                new Method("getMetricsForYamas", "s", "s", this::getMetricsForYamas)
                        .methodDesc("Get JSON formatted Vespa metrics for a given service name or 'all'")
                        .paramDesc(0, "service", "The vespa service name, or 'all'")
                        .returnDesc(0, "ret", "Vespa metrics"));

        connector.addMethod(
                new Method("getHealthMetricsForYamas", "s", "s", this::getHealthMetricsForYamas)
                        .methodDesc("Get JSON formatted Health check for a given service name or 'all'")
                        .paramDesc(0, "service", "The vespa service name")
                        .returnDesc(0, "ret", "Vespa metrics"));

        connector.addMethod(
                new Method("getAllMetricNamesForService", "ss", "s", this::getAllMetricNamesForService)
                        .methodDesc("Get metric names known for service ")
                        .paramDesc(0, "service", "The vespa service name'")
                        .paramDesc(1, "consumer", "The consumer'")
                        .returnDesc(0, "ret", "Metric names, one metric name per line"));
    }

    void getAllMetricNamesForService(Request req) {
        String service = req.parameters().get(0).asString();
        ConsumerId consumer = toConsumerId(req.parameters().get(1).asString());
        withExceptionHandling(req, () -> {
            String metricNames = metricsManager.getMetricNamesForServiceAndConsumer(service, consumer);
            req.returnValues().add(new StringValue(metricNames));
        });
    }

    void getMetricsById(Request req) {
        String id = req.parameters().get(0).asString();
        withExceptionHandling(req, () -> {
            String metricsString = metricsManager.getMetricsByConfigId(id);
            req.returnValues().add(new StringValue(metricsString));
        });
    }


    void getServices(Request req) {
        withExceptionHandling(req, () -> {
            String servicesString = metricsManager.getAllVespaServices();
            req.returnValues().add(new StringValue(servicesString));
        });
    }

    void getMetricsForYamas(Request req) {
        Instant startTime = Instant.now();
        req.detach();
        String service = req.parameters().get(0).asString();
        log.log(FINE, () -> "getMetricsForYamas called at " + startTime + " with argument: " + service);
        List<VespaService> services = vespaServices.getMonitoringServices(service);
        log.log(FINE, () -> "Getting metrics for services: " + mkString(services, "[", ", ", "]"));
        if (services.isEmpty()) setNoServiceError(req, service);
        else withExceptionHandling(req, () -> {
            List<MetricsPacket> packets = metricsManager.getMetrics(services, startTime);
            log.log(FINE,() -> "Returning metrics packets:\n" + mkString(packets, "\n"));
            req.returnValues().add(new StringValue(toYamasArray(packets).serialize()));
        });
        req.returnRequest();
    }

    void getHealthMetricsForYamas(Request req) {
        req.detach();
        String service = req.parameters().get(0).asString();
        List<VespaService> services = vespaServices.getMonitoringServices(service);
        if (services.isEmpty()) setNoServiceError(req, service);
        else withExceptionHandling(req, () -> {
            List<MetricsPacket> packets = metricsManager.getHealthMetrics(services);
            req.returnValues().add(new StringValue(toYamasArray(packets, true).serialize()));
        });
        req.returnRequest();
    }

    void setExtraMetrics(Request req) {
        String metricsJson = req.parameters().get(0).asString();
        log.log(FINE, "setExtraMetrics called with argument: " + metricsJson);
        withExceptionHandling(req, () -> metricsManager.setExtraMetrics(toMetricsPackets(metricsJson)));
    }

    void purgeExtraMetrics(Request req) {
        withExceptionHandling(req, metricsManager::purgeExtraMetrics);
    }

    private static void withExceptionHandling(Request req, ThrowingRunnable runnable) {
        try {
            TimeTracker timeTracker = new TimeTracker(req);
            runnable.run();
            timeTracker.logSpentTime();
        } catch (Exception e) {
            log.log(WARNING, "Got exception when running RPC command " + req.methodName(), e);
            setMethodFailedError(req, e);
        } catch (Error e) {
            log.log(WARNING, "Got error when running RPC command " + req.methodName(), e);
            setMethodFailedError(req, e);
        } catch (Throwable t) {
            log.log(WARNING, "Got throwable (non-error, non-exception) when running RPC command " + req.methodName(), t);
            setMethodFailedError(req, t);
        }
    }

    private static void setMethodFailedError(Request req, Throwable t) {
        String msg = "Request failed due to internal error: " + t.getClass().getName() + ": " + t.getMessage();
        req.setError(ErrorCode.METHOD_FAILED, msg);
        req.returnValues().add(new StringValue(""));
    }

    private static void setNoServiceError(Request req, String serviceName) {
        String msg = "No service with name '" + serviceName + "'";
        req.setError(ErrorCode.BAD_REQUEST, msg);
        req.returnValues().add(new StringValue(""));
    }


    private static class TimeTracker {
        private final long startTime = System.currentTimeMillis();
        private final Request request;

        private TimeTracker(Request request) {
            this.request = request;
        }

        long spentTime() {
            return System.currentTimeMillis() - startTime;
        }

        private void logSpentTime() {
            Level logLevel = Level.FINE;
            if (spentTime() > LOG_SPENT_TIME_LIMIT) {
                logLevel = Level.INFO;
            }
            if (log.isLoggable(logLevel)) {
                log.log(logLevel, "RPC request '" + request.methodName() + "' with parameters '" +
                        request.parameters() + "' took " + spentTime() + " ms");
            }
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

}
