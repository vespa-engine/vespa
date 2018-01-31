// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.config.FileReference;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostLivenessTracker;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.DataValue;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Int64Value;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ErrorCode;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.config.JRTMethods;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.vespa.config.server.SuperModelRequestHandler;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.GetConfigContext;
import com.yahoo.vespa.config.server.filedistribution.FileServer;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.ReloadListener;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.MetricUpdaterFactory;
import com.yahoo.vespa.config.server.tenant.TenantHandlerProvider;
import com.yahoo.vespa.config.server.tenant.TenantListener;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.FileReceiver;
import com.yahoo.vespa.filedistribution.FileReferenceData;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An RPC server class that handles the config protocol RPC method "getConfigV3".
 * Mandatory hooks need to be implemented by subclasses.
 *
 * @author hmusum
 */
// TODO: Split business logic out of this
public class RpcServer implements Runnable, ReloadListener, TenantListener {

    public static final String getConfigMethodName = "getConfigV3";
    
    static final int TRACELEVEL = 6;
    static final int TRACELEVEL_DEBUG = 9;
    private static final String THREADPOOL_NAME = "rpcserver worker pool";
    private static final long SHUTDOWN_TIMEOUT = 60;

    private final Supervisor supervisor = new Supervisor(new Transport());
    private Spec spec;
    private final boolean useRequestVersion;
    private final boolean hostedVespa;

    private static final Logger log = Logger.getLogger(RpcServer.class.getName());

    final DelayedConfigResponses delayedConfigResponses;

    private final HostRegistry<TenantName> hostRegistry;
    private final Map<TenantName, TenantHandlerProvider> tenantProviders = new ConcurrentHashMap<>();
    private final SuperModelRequestHandler superModelRequestHandler;
    private final MetricUpdater metrics;
    private final MetricUpdaterFactory metricUpdaterFactory;
    private final HostLivenessTracker hostLivenessTracker;
    private final FileServer fileServer;
    
    private final ThreadPoolExecutor executorService;
    private final boolean useChunkedFileTransfer;
    private final FileDownloader downloader;
    private volatile boolean allTenantsLoaded = false;

    /**
     * Creates an RpcServer listening on the specified <code>port</code>.
     *
     * @param config The config to use for setting up this server
     */
    @Inject
    public RpcServer(ConfigserverConfig config, SuperModelRequestHandler superModelRequestHandler,
                     MetricUpdaterFactory metrics, HostRegistries hostRegistries,
                     HostLivenessTracker hostLivenessTracker, FileServer fileServer) {
        this.superModelRequestHandler = superModelRequestHandler;
        metricUpdaterFactory = metrics;
        supervisor.setMaxOutputBufferSize(config.maxoutputbuffersize());
        this.metrics = metrics.getOrCreateMetricUpdater(Collections.<String, String>emptyMap());
        this.hostLivenessTracker = hostLivenessTracker;
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(config.maxgetconfigclients());
        executorService = new ThreadPoolExecutor(config.numthreads(), config.numthreads(),
                0, TimeUnit.SECONDS, workQueue, ThreadFactoryFactory.getThreadFactory(THREADPOOL_NAME));
        delayedConfigResponses = new DelayedConfigResponses(this, config.numDelayedResponseThreads());
        spec = new Spec(null, config.rpcport());
        hostRegistry = hostRegistries.getTenantHostRegistry();
        this.useRequestVersion = config.useVespaVersionInRequest();
        this.hostedVespa = config.hostedVespa();
        this.fileServer = fileServer;
        this.useChunkedFileTransfer = config.usechunkedtransfer();
        downloader = fileServer.downloader();
        setUpHandlers();
    }

    /**
     * Called by reflection from RCP.
     * Handles RPC method "config.v3.getConfig" requests.
     * Uses the template pattern to call methods in classes that extend RpcServer.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public final void getConfigV3(Request req) {
        if (log.isLoggable(LogLevel.SPAM)) {
            log.log(LogLevel.SPAM, getConfigMethodName);
        }
        req.detach();
        JRTServerConfigRequestV3 request = JRTServerConfigRequestV3.createFromRequest(req);
        addToRequestQueue(request);
        hostLivenessTracker.receivedRequestFrom(request.getClientHostName());
    }

    /**
     * Called by reflection from RCP.
     * Returns 0 if server is alive.
     */
    @SuppressWarnings("UnusedDeclaration")
    public final void ping(Request req) {
        req.returnValues().add(new Int32Value(0));
    }

    /**
     * Called by reflection from RCP.
     * Returns a String with statistics data for the server.
     *
     * @param req a Request
     */
    public final void printStatistics(Request req) {
        req.returnValues().add(new StringValue("Delayed responses queue size: " + delayedConfigResponses.size()));
    }

    public void run() {
        log.log(LogLevel.INFO, "Rpc server listening on port " + spec.port());
        try {
            Acceptor acceptor = supervisor.listen(spec);
            supervisor.transport().join();
            acceptor.shutdown().join();
        } catch (ListenFailedException e) {
            stop();
            throw new RuntimeException("Could not listen at " + spec, e);
        }
    }

    public void stop() {
        executorService.shutdown();
        try {
            executorService.awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.interrupted(); // Ignore and continue shutdown.
        }
        delayedConfigResponses.stop();
        supervisor.transport().shutdown().join();
    }

    /**
     * Set up RPC method handlers.
     */
    private void setUpHandlers() {
        // The getConfig method in this class will handle RPC calls for getting config
        getSupervisor().addMethod(JRTMethods.createConfigV3GetConfigMethod(this, getConfigMethodName));
        getSupervisor().addMethod(new Method("ping", "", "i", this, "ping")
                                  .methodDesc("ping")
                                  .returnDesc(0, "ret code", "return code, 0 is OK"));
        getSupervisor().addMethod(new Method("printStatistics", "", "s", this, "printStatistics")
                                  .methodDesc("printStatistics")
                                  .returnDesc(0, "statistics", "Statistics for server"));
        getSupervisor().addMethod(new Method("filedistribution.serveFile", "s", "is", this, "serveFile"));
        getSupervisor().addMethod(new Method("filedistribution.setFileReferencesToDownload", "S", "i",
                                        this, "setFileReferencesToDownload")
                                     .methodDesc("set which file references to download")
                                     .paramDesc(0, "file references", "file reference to download")
                                     .returnDesc(0, "ret", "0 if success, 1 otherwise"));
    }

    /**
     * Checks all delayed responses for config changes and waits until all has been answered.
     * This method should be called when config is reloaded in the server.
     */
    @Override
    public void configActivated(TenantName tenant, ApplicationSet applicationSet) {
        ApplicationId applicationId = applicationSet.getId();
        configReloaded(delayedConfigResponses.drainQueue(applicationId), Tenants.logPre(applicationId));
        reloadSuperModel(tenant, applicationSet);
    }

    private void reloadSuperModel(TenantName tenant, ApplicationSet applicationSet) {
        superModelRequestHandler.reloadConfig(tenant, applicationSet);
        configReloaded(delayedConfigResponses.drainQueue(ApplicationId.global()), Tenants.logPre(ApplicationId.global()));
    }

    private void configReloaded(List<DelayedConfigResponses.DelayedConfigResponse> responses, String logPre) {
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, logPre + "Start of configReload: " + responses.size() + " requests on delayed requests queue");
        }
        int responsesSent = 0;
        CompletionService<Boolean> completionService = new ExecutorCompletionService<>(executorService);
        while (!responses.isEmpty()) {
            DelayedConfigResponses.DelayedConfigResponse delayedConfigResponse = responses.remove(0);
            // Discard the ones that we have already answered
            // Doing cancel here deals with the case where the timer is already running or has not run, so
            // there is no need for any extra check.
            if (delayedConfigResponse.cancel()) {
                if (log.isLoggable(LogLevel.DEBUG)) {
                    logRequestDebug(LogLevel.DEBUG, logPre + "Timer cancelled for ", delayedConfigResponse.request);
                }
                // Do not wait for this request if we were unable to execute
                if (addToRequestQueue(delayedConfigResponse.request, false, completionService)) {
                    responsesSent++;
                }
            } else {
                log.log(LogLevel.DEBUG, logPre + "Timer already cancelled or finished or never scheduled");
            }
        }

        for (int i = 0; i < responsesSent; i++) {
            try {
                completionService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.log(LogLevel.DEBUG, logPre + "Finished reloading " + responsesSent + " requests");
    }

    private void logRequestDebug(LogLevel level, String message, JRTServerConfigRequest request) {
        if (log.isLoggable(level)) {
            log.log(level, message + request.getShortDescription());
        }
    }

    @Override
    public void hostsUpdated(TenantName tenant, Collection<String> newHosts) {
        log.log(LogLevel.DEBUG, "Updating hosts in tenant host registry '" + hostRegistry + "' with " + newHosts);
        hostRegistry.update(tenant, newHosts);
    }

    @Override
    public void verifyHostsAreAvailable(TenantName tenant, Collection<String> newHosts) {
        hostRegistry.verifyHosts(tenant, newHosts);
    }

    @Override
    public void applicationRemoved(ApplicationId applicationId) {
        superModelRequestHandler.removeApplication(applicationId);
        configReloaded(delayedConfigResponses.drainQueue(applicationId), Tenants.logPre(applicationId));
        configReloaded(delayedConfigResponses.drainQueue(ApplicationId.global()), Tenants.logPre(ApplicationId.global()));
    }

    public void respond(JRTServerConfigRequest request) {
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "Trace at request return:\n" + request.getRequestTrace().toString());
        }
        request.getRequest().returnRequest();
    }

    /**
     * Returns the tenant for this request, empty if there is no tenant for this request
     * (which on hosted Vespa means that the requesting host is not currently active for any tenant)
     */
    public Optional<TenantName> resolveTenant(JRTServerConfigRequest request, Trace trace) {
        if ("*".equals(request.getConfigKey().getConfigId())) return Optional.of(ApplicationId.global().tenant());
        String hostname = request.getClientHostName();
        TenantName tenant = hostRegistry.getKeyForHost(hostname);
        if (tenant == null) {
            if (GetConfigProcessor.logDebug(trace)) {
                String message = "Did not find tenant for host '" + hostname + "', using " + TenantName.defaultName();
                log.log(LogLevel.DEBUG, message);
                log.log(LogLevel.DEBUG, "hosts in host registry: " + hostRegistry.getAllHosts());
                trace.trace(6, message);
            }
            return Optional.empty();
        }
        return Optional.of(tenant);
    }

    public ConfigResponse resolveConfig(JRTServerConfigRequest request, GetConfigContext context, Optional<Version> vespaVersion) {
        context.trace().trace(TRACELEVEL, "RpcServer.resolveConfig()");
        return context.requestHandler().resolveConfig(context.applicationId(), request, vespaVersion);
    }

    protected Supervisor getSupervisor() {
        return supervisor;
    }

    Boolean addToRequestQueue(JRTServerConfigRequest request) {
        return addToRequestQueue(request, false, null);
    }

    public Boolean addToRequestQueue(JRTServerConfigRequest request, boolean forceResponse, CompletionService<Boolean> completionService) {
        // It's no longer delayed if we get here
        request.setDelayedResponse(false);
        //ConfigDebug.logDebug(log, System.currentTimeMillis(), request.getConfigKey(), "RpcServer.addToRequestQueue()");
        try {
            final GetConfigProcessor task = new GetConfigProcessor(this, request, forceResponse);
            if (completionService == null) {
                executorService.submit(task);
            } else {
                completionService.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        task.run();
                        return true;
                    }
                });
            }
            updateWorkQueueMetrics();
            return true;
        } catch (RejectedExecutionException e) {
            request.addErrorResponse(ErrorCode.INTERNAL_ERROR, "getConfig request queue size is larger than configured max limit");
            respond(request);
            return false;
        }
    }

    private void updateWorkQueueMetrics() {
        int queued = executorService.getQueue().size();
        metrics.setRpcServerQueueSize(queued);
    }

    /**
     * Returns the context for this request, or null if the server is not properly set up with handlers
     */
    public GetConfigContext createGetConfigContext(Optional<TenantName> optionalTenant, JRTServerConfigRequest request, Trace trace) {
        if ("*".equals(request.getConfigKey().getConfigId())) {
            return GetConfigContext.create(ApplicationId.global(), superModelRequestHandler, trace);
        }
        TenantName tenant = optionalTenant.orElse(TenantName.defaultName()); // perhaps needed for non-hosted?
        if ( ! hasRequestHandler(tenant)) {
            String msg = Tenants.logPre(tenant) + "Unable to find request handler for tenant. Requested from host '" + request.getClientHostName() + "'";
            metrics.incUnknownHostRequests();
            trace.trace(TRACELEVEL, msg);
            log.log(LogLevel.WARNING, msg);
            return null;
        }
        RequestHandler handler = getRequestHandler(tenant);
        ApplicationId applicationId = handler.resolveApplicationId(request.getClientHostName());
        if (trace.shouldTrace(TRACELEVEL_DEBUG)) {
            trace.trace(TRACELEVEL_DEBUG, "Host '" + request.getClientHostName() + "' should have config from application '" + applicationId + "'");
        }
        return GetConfigContext.create(applicationId, handler, trace);
    }

    private boolean hasRequestHandler(TenantName tenant) {
        return tenantProviders.containsKey(tenant);
    }

    private RequestHandler getRequestHandler(TenantName tenant) {
        if (!tenantProviders.containsKey(tenant)) {
            throw new IllegalStateException("No request handler for " + tenant);
        }
        return tenantProviders.get(tenant).getRequestHandler();
    }

    public void delayResponse(JRTServerConfigRequest request, GetConfigContext context) {
        delayedConfigResponses.delayResponse(request, context);
    }

    @Override
    public void onTenantDelete(TenantName tenant) {
        log.log(LogLevel.DEBUG, Tenants.logPre(tenant)+"Tenant deleted, removing request handler and cleaning host registry");
        if (tenantProviders.containsKey(tenant)) {
            tenantProviders.remove(tenant);
        }
        hostRegistry.removeHostsForKey(tenant);
    }

    @Override
    public void onTenantsLoaded() {
        allTenantsLoaded = true;
        superModelRequestHandler.enable();
    }

    @Override
    public void onTenantCreate(TenantName tenant, TenantHandlerProvider tenantHandlerProvider) {
        log.log(LogLevel.DEBUG, Tenants.logPre(tenant)+"Tenant created, adding request handler");
        tenantProviders.put(tenant, tenantHandlerProvider);
    }

    /** Returns true only after all tenants are loaded */
    public boolean allTenantsLoaded() { return allTenantsLoaded; }

    /** Returns true if this rpc server is currently running in a hosted Vespa configuration */
    public boolean isHostedVespa() { return hostedVespa; }
    
    MetricUpdaterFactory metricUpdaterFactory() {
        return metricUpdaterFactory;
    }

    boolean useRequestVersion() {
        return useRequestVersion;
    }

    class WholeFileReceiver implements FileServer.Receiver {
        Target target;
        WholeFileReceiver(Target target) {
            this.target = target;
        }

        @Override
        public String toString() {
            return target.toString();
        }

        @Override
        public void receive(FileReferenceData fileData, FileServer.ReplayStatus status) {
            Request fileBlob = new Request(FileReceiver.RECEIVE_METHOD);
            fileBlob.parameters().add(new StringValue(fileData.fileReference().value()));
            fileBlob.parameters().add(new StringValue(fileData.filename()));
            fileBlob.parameters().add(new StringValue(fileData.type().name()));
            fileBlob.parameters().add(new DataValue(fileData.content().array()));
            fileBlob.parameters().add(new Int64Value(fileData.xxhash()));
            fileBlob.parameters().add(new Int32Value(status.getCode()));
            fileBlob.parameters().add(new StringValue(status.getDescription()));
            target.invokeSync(fileBlob, 600);
            if (fileBlob.isError()) {
                log.warning("Failed delivering reference '" + fileData.fileReference().value() + "' with file '" + fileData.filename() + "' to " +
                            target.toString() + " with error: '" + fileBlob.errorMessage() + "'.");
            }
        }
    }

    class ChunkedFileReceiver implements FileServer.Receiver {
        Target target;
        ChunkedFileReceiver(Target target) {
            this.target = target;
        }

        @Override
        public String toString() {
            return target.toString();
        }

        @Override
        public void receive(FileReferenceData fileData, FileServer.ReplayStatus status) {
            int session = sendMeta(fileData);
            sendParts(session, fileData);
            sendEof(session, fileData, status);
        }
        private void sendParts(int session, FileReferenceData fileData) {
            ByteBuffer bb = ByteBuffer.allocate(0x100000);
            for (int partId = 0, read = fileData.nextContent(bb); read >= 0; partId++, read = fileData.nextContent(bb)) {
                byte [] buf = bb.array();
                if (buf.length != bb.position()) {
                    buf = new byte [bb.position()];
                    bb.flip();
                    bb.get(buf);
                }
                sendPart(session, fileData.fileReference(), partId, buf);
                bb.clear();
            }
        }
        private int sendMeta(FileReferenceData fileData) {
            Request request = new Request(FileReceiver.RECEIVE_META_METHOD);
            request.parameters().add(new StringValue(fileData.fileReference().value()));
            request.parameters().add(new StringValue(fileData.filename()));
            request.parameters().add(new StringValue(fileData.type().name()));
            request.parameters().add(new Int64Value(fileData.size()));
            target.invokeSync(request, 600);
            if (request.isError()) {
                log.warning("Failed delivering meta for reference '" + fileData.fileReference().value() + "' with file '" + fileData.filename() + "' to " +
                        target.toString() + " with error: '" + request.errorMessage() + "'.");
                return 1;
            } else {
                if (request.returnValues().get(0).asInt32() != 0) {
                    throw new IllegalArgumentException("Unknown error from target '" + target.toString() + "' during rpc call " + request.methodName());
                }
                return request.returnValues().get(1).asInt32();
            }
        }
        private void sendPart(int session, FileReference ref, int partId, byte [] buf) {
            Request request = new Request(FileReceiver.RECEIVE_PART_METHOD);
            request.parameters().add(new StringValue(ref.value()));
            request.parameters().add(new Int32Value(session));
            request.parameters().add(new Int32Value(partId));
            request.parameters().add(new DataValue(buf));
            target.invokeSync(request, 600);
            if (request.isError()) {
                throw new IllegalArgumentException("Failed delivering reference '" + ref.value() + "' to " +
                                                           target.toString() + " with error: '" + request.errorMessage() + "'.");
            } else {
                if (request.returnValues().get(0).asInt32() != 0) {
                    throw new IllegalArgumentException("Unknown error from target '" + target.toString() + "' during rpc call " + request.methodName());
                }
            }
        }
        private void sendEof(int session, FileReferenceData fileData, FileServer.ReplayStatus status) {
            Request request = new Request(FileReceiver.RECEIVE_EOF_METHOD);
            request.parameters().add(new StringValue(fileData.fileReference().value()));
            request.parameters().add(new Int32Value(session));
            request.parameters().add(new Int64Value(fileData.xxhash()));
            request.parameters().add(new Int32Value(status.getCode()));
            request.parameters().add(new StringValue(status.getDescription()));
            target.invokeSync(request, 600);
            if (request.isError()) {
                throw new IllegalArgumentException("Failed delivering reference '" + fileData.fileReference().value() + "' with file '" + fileData.filename() + "' to " +
                                                           target.toString() + " with error: '" + request.errorMessage() + "'.");
            } else {
                if (request.returnValues().get(0).asInt32() != 0) {
                    throw new IllegalArgumentException("Unknown error from target '" + target.toString() + "' during rpc call " + request.methodName());
                }
            }
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public final void serveFile(Request request) {
        request.detach();
        FileServer.Receiver receiver = useChunkedFileTransfer
                                       ? new ChunkedFileReceiver(request.target())
                                       : new WholeFileReceiver(request.target());
        fileServer.serveFile(request, receiver);
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void setFileReferencesToDownload(Request req) {
        String[] fileReferenceStrings = req.parameters().get(0).asStringArray();
        List<FileReference> fileReferences = Stream.of(fileReferenceStrings)
                .map(FileReference::new)
                .collect(Collectors.toList());
        downloader.queueForAsyncDownload(fileReferences);

        req.returnValues().add(new Int32Value(0));
    }
}
