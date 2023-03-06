// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.config.FileReference;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
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
import com.yahoo.security.tls.Capability;
import com.yahoo.vespa.config.ErrorCode;
import com.yahoo.vespa.config.JRTMethods;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.vespa.config.server.ConfigActivationListener;
import com.yahoo.vespa.config.server.GetConfigContext;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.SuperModelRequestHandler;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.filedistribution.FileServer;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.MetricUpdaterFactory;
import com.yahoo.vespa.config.server.rpc.security.RpcAuthorizer;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantListener;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.FileReceiver;
import com.yahoo.vespa.filedistribution.FileReferenceData;
import com.yahoo.vespa.filedistribution.FileReferenceDownload;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType;

/**
 * An RPC server class that handles the config protocol RPC method "getConfigV3".
 * Mandatory hooks need to be implemented by subclasses.
 *
 * @author hmusum
 */
// TODO: Split business logic out of this
public class RpcServer implements Runnable, ConfigActivationListener, TenantListener {

    static final String getConfigMethodName = "getConfigV3";
    
    private static final int TRACELEVEL = 6;
    static final int TRACELEVEL_DEBUG = 9;
    private static final String THREADPOOL_NAME = "rpcserver worker pool";
    private static final long SHUTDOWN_TIMEOUT = 60;
    private static final int JRT_RPC_TRANSPORT_THREADS = threadsToUse();

    private final Supervisor supervisor = new Supervisor(new Transport("rpc", JRT_RPC_TRANSPORT_THREADS));
    private final Spec spec;
    private final boolean useRequestVersion;
    private final boolean hostedVespa;
    private final boolean canReturnEmptySentinelConfig;

    private static final Logger log = Logger.getLogger(RpcServer.class.getName());

    private final DelayedConfigResponses delayedConfigResponses;

    private final HostRegistry hostRegistry;
    private final Map<TenantName, Tenant> tenants = new ConcurrentHashMap<>();
    private final Map<ApplicationId, ApplicationState> applicationStateMap = new ConcurrentHashMap<>();
    private final SuperModelRequestHandler superModelRequestHandler;
    private final MetricUpdater metrics;
    private final MetricUpdaterFactory metricUpdaterFactory;
    private final FileServer fileServer;
    private final RpcAuthorizer rpcAuthorizer;

    private final ThreadPoolExecutor executorService;
    private final FileDownloader downloader;
    private volatile boolean allTenantsLoaded = false;
    private boolean isRunning = false;
    boolean isServingConfigRequests = false;

    static class ApplicationState {
        private final AtomicLong activeGeneration = new AtomicLong(0);
        ApplicationState(long generation) {
            activeGeneration.set(generation);
        }
        long getActiveGeneration() { return activeGeneration.get(); }
        void setActiveGeneration(long generation) { activeGeneration.set(generation); }
    }
    /**
     * Creates an RpcServer listening on the specified <code>port</code>.
     *
     * @param config The config to use for setting up this server
     */
    @Inject
    public RpcServer(ConfigserverConfig config, SuperModelRequestHandler superModelRequestHandler,
                     MetricUpdaterFactory metrics, HostRegistry hostRegistry,
                     FileServer fileServer, RpcAuthorizer rpcAuthorizer,
                     RpcRequestHandlerProvider handlerProvider) {
        this.superModelRequestHandler = superModelRequestHandler;
        metricUpdaterFactory = metrics;
        supervisor.setMaxOutputBufferSize(config.maxoutputbuffersize());
        this.metrics = metrics.getOrCreateMetricUpdater(Collections.emptyMap());
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(config.maxgetconfigclients());
        int rpcWorkerThreads = (config.numRpcThreads() == 0) ? threadsToUse() : config.numRpcThreads();
        executorService = new ThreadPoolExecutor(rpcWorkerThreads, rpcWorkerThreads,
                0, TimeUnit.SECONDS, workQueue, ThreadFactoryFactory.getDaemonThreadFactory(THREADPOOL_NAME));
        delayedConfigResponses = new DelayedConfigResponses(this, config.numDelayedResponseThreads());
        spec = new Spec(null, config.rpcport());
        this.hostRegistry = hostRegistry;
        this.useRequestVersion = config.useVespaVersionInRequest();
        this.hostedVespa = config.hostedVespa();
        this.canReturnEmptySentinelConfig = config.canReturnEmptySentinelConfig();
        this.fileServer = fileServer;
        this.rpcAuthorizer = rpcAuthorizer;
        downloader = fileServer.downloader();
        handlerProvider.setInstance(this);
        setUpFileDistributionHandlers();
    }

    private static int threadsToUse() {
        return Math.max(8, Runtime.getRuntime().availableProcessors()/2);
    }

    /**
     * Handles RPC method "config.v3.getConfig" requests.
     * Uses the template pattern to call methods in classes that extend RpcServer.
     */
    private void getConfigV3(Request req) {
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST, getConfigMethodName);
        }
        req.detach();
        rpcAuthorizer.authorizeConfigRequest(req)
                .thenRun(() -> addToRequestQueue(JRTServerConfigRequestV3.createFromRequest(req)));
    }

    /**
     * Returns 0 if server is alive.
     */
    private void ping(Request req) {
        req.returnValues().add(new Int32Value(0));
    }

    /**
     * Returns a String with statistics data for the server.
     *
     * @param req a Request
     */
    private void printStatistics(Request req) {
        req.returnValues().add(new StringValue("Delayed responses queue size: " + delayedConfigResponses.size()));
    }

    @Override
    public void run() {
        log.log(Level.FINE, "Rpc server will listen on port " + spec.port());
        try {
            Acceptor acceptor = supervisor.listen(spec);
            isRunning = true;
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
        fileServer.close();
        supervisor.transport().shutdown().join();
        isRunning = false;
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Set up RPC method handlers, except handlers for getting config (see #setUpGetConfigHandlers())
     */
    private void setUpFileDistributionHandlers() {
        getSupervisor().addMethod(new Method("ping", "", "i", this::ping)
                                  .methodDesc("ping")
                                  .returnDesc(0, "ret code", "return code, 0 is OK"));
        getSupervisor().addMethod(new Method("printStatistics", "", "s", this::printStatistics)
                                  .methodDesc("printStatistics")
                                  .returnDesc(0, "statistics", "Statistics for server"));
        getSupervisor().addMethod(new Method("filedistribution.serveFile", "si*", "is", this::serveFile)
                                  .requireCapabilities(Capability.CONFIGSERVER__FILEDISTRIBUTION_API));
        getSupervisor().addMethod(new Method("filedistribution.setFileReferencesToDownload", "S", "i", this::setFileReferencesToDownload)
                                  .requireCapabilities(Capability.CONFIGSERVER__FILEDISTRIBUTION_API)
                                  .methodDesc("set which file references to download")
                                  .paramDesc(0, "file references", "file reference to download")
                                  .returnDesc(0, "ret", "0 if success, 1 otherwise"));
    }

    /**
     * Set up RPC method handlers for getting config
     */
    public void setUpGetConfigHandlers() {
        // The getConfig method in this class will handle RPC calls for getting config
        getSupervisor().addMethod(JRTMethods.createConfigV3GetConfigMethod(this::getConfigV3)
                                          .requireCapabilities(Capability.CONFIGSERVER__CONFIG_API));
        isServingConfigRequests = true;
    }


    public boolean isServingConfigRequests() {
        return isServingConfigRequests;
    }

    private ApplicationState getState(ApplicationId id) {
        return applicationStateMap.computeIfAbsent(id, __ -> new ApplicationState(0));
    }

    boolean hasNewerGeneration(ApplicationId id, long generation) {
        return getState(id).getActiveGeneration() > generation;
    }

    /**
     * Checks all delayed responses for config changes and waits until all has been answered.
     * This method should be called when config is activated in the server.
     */
    @Override
    public void configActivated(ApplicationSet applicationSet) {
        ApplicationId applicationId = applicationSet.getId();
        ApplicationState state = getState(applicationId);
        state.setActiveGeneration(applicationSet.getApplicationGeneration());
        reloadSuperModel(applicationSet);
        configActivated(applicationId);
    }

    private void reloadSuperModel(ApplicationSet applicationSet) {
        superModelRequestHandler.activateConfig(applicationSet);
        configActivated(ApplicationId.global());
    }

    void configActivated(ApplicationId applicationId) {
        List<DelayedConfigResponses.DelayedConfigResponse> responses = delayedConfigResponses.drainQueue(applicationId);
        String logPre = TenantRepository.logPre(applicationId);
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, logPre + "Start of configActivated: " + responses.size() + " requests on delayed requests queue");
        }
        int responsesSent = 0;
        CompletionService<Boolean> completionService = new ExecutorCompletionService<>(executorService);
        while (!responses.isEmpty()) {
            DelayedConfigResponses.DelayedConfigResponse delayedConfigResponse = responses.remove(0);
            // Discard the ones that we have already answered
            // Doing cancel here deals with the case where the timer is already running or has not run, so
            // there is no need for any extra check.
            if (delayedConfigResponse.cancel()) {
                if (log.isLoggable(Level.FINE)) {
                    logRequestDebug(Level.FINE, logPre + "Timer cancelled for ", delayedConfigResponse.request);
                }
                // Do not wait for this request if we were unable to execute
                if (addToRequestQueue(delayedConfigResponse.request, false, completionService)) {
                    responsesSent++;
                }
            } else {
                log.log(Level.FINE, () -> logPre + "Timer already cancelled or finished or never scheduled");
            }
        }

        for (int i = 0; i < responsesSent; i++) {
            try {
                completionService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (log.isLoggable(Level.FINE))
            log.log(Level.FINE, logPre + "Finished activating " + responsesSent + " requests");
    }

    private void logRequestDebug(Level level, String message, JRTServerConfigRequest request) {
        if (log.isLoggable(level)) {
            log.log(level, message + request.getShortDescription());
        }
    }

    @Override
    public void applicationRemoved(ApplicationId applicationId) {
        superModelRequestHandler.removeApplication(applicationId);
        configActivated(applicationId);
        configActivated(ApplicationId.global());
    }

    public void respond(JRTServerConfigRequest request) {
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Trace at request return:\n" + request.getRequestTrace().toString());
        }
        request.getRequest().returnRequest();
    }

    /**
     * Returns the tenant for this request, empty if there is none
     * (which on hosted Vespa means that the requesting host is not currently active for any tenant)
     */
    Optional<TenantName> resolveTenant(JRTServerConfigRequest request, Trace trace) {
        if ("*".equals(request.getConfigKey().getConfigId())) return Optional.of(ApplicationId.global().tenant());

        String hostname = request.getClientHostName();
        ApplicationId applicationId = hostRegistry.getApplicationId(hostname);
        if (applicationId == null) {
            if (GetConfigProcessor.logDebug(trace)) {
                String message = "Did not find tenant for host '" + hostname + "', using " + TenantName.defaultName() +
                                 ". Hosts in host registry: " + hostRegistry.getAllHosts();
                log.log(Level.FINE, () -> message);
                trace.trace(6, message);
            }
            return Optional.empty();
        }
        return Optional.of(applicationId.tenant());
    }

    public ConfigResponse resolveConfig(JRTServerConfigRequest request, GetConfigContext context, Optional<Version> vespaVersion) {
        context.trace().trace(TRACELEVEL, "RpcServer.resolveConfig()");
        return context.requestHandler().resolveConfig(context.applicationId(), request, vespaVersion);
    }

    private Supervisor getSupervisor() {
        return supervisor;
    }

    private void addToRequestQueue(JRTServerConfigRequest request) {
        addToRequestQueue(request, false, null);
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
                completionService.submit(() -> { task.run(); return true; });
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
    GetConfigContext createGetConfigContext(Optional<TenantName> optionalTenant, JRTServerConfigRequest request, Trace trace) {
        if ("*".equals(request.getConfigKey().getConfigId())) {
            return GetConfigContext.create(ApplicationId.global(), superModelRequestHandler, trace);
        }
        // TODO: Look into if this fallback really is needed
        TenantName tenant = optionalTenant.orElse(TenantName.defaultName());
        Optional<RequestHandler> requestHandler = getRequestHandler(tenant);
        if (requestHandler.isEmpty()) {
            String msg = TenantRepository.logPre(tenant) + "Unable to find request handler for tenant '" + tenant  +
                         "'. Request from host '" + request.getClientHostName() + "'";
            metrics.incUnknownHostRequests();
            trace.trace(TRACELEVEL, msg);
            log.log(Level.WARNING, msg);
            return GetConfigContext.empty();
        }
        RequestHandler handler = requestHandler.get();
        ApplicationId applicationId = handler.resolveApplicationId(request.getClientHostName());
        // TODO: Look into if this fallback really is needed
        if (applicationId == null && tenant.equals(TenantName.defaultName()))
                applicationId = ApplicationId.defaultId();
        if (trace.shouldTrace(TRACELEVEL_DEBUG)) {
            trace.trace(TRACELEVEL_DEBUG, "Host '" + request.getClientHostName() + "' should have config from application '" + applicationId + "'");
        }
        return GetConfigContext.create(applicationId, handler, trace);
    }

    Optional<RequestHandler> getRequestHandler(TenantName tenant) {
        return Optional.ofNullable(tenants.get(tenant))
                .map(Tenant::getRequestHandler);
    }

    void delayResponse(JRTServerConfigRequest request, GetConfigContext context) {
        delayedConfigResponses.delayResponse(request, context);
    }

    @Override
    public void onTenantDelete(TenantName tenant) {
        log.log(Level.FINE, () -> TenantRepository.logPre(tenant) +
                            "Tenant deleted, removing request handler and cleaning host registry");
        tenants.remove(tenant);
    }

    @Override
    public void onTenantsLoaded() {
        allTenantsLoaded = true;
        superModelRequestHandler.enable();
    }

    @Override
    public void onTenantCreate(Tenant tenant) {
        tenants.put(tenant.getName(), tenant);
    }

    /** Returns true only after all tenants are loaded */
    public boolean allTenantsLoaded() { return allTenantsLoaded; }

    /** Returns true if this rpc server is currently running in a hosted Vespa configuration */
    public boolean isHostedVespa() { return hostedVespa; }

    /** Returns true if empty sentinel config can be returned when a request from a host that is
     * not part of an application asks for sentinel config */
    public boolean canReturnEmptySentinelConfig() { return canReturnEmptySentinelConfig; }
    
    MetricUpdaterFactory metricUpdaterFactory() {
        return metricUpdaterFactory;
    }

    boolean useRequestVersion() {
        return useRequestVersion;
    }

    static class ChunkedFileReceiver implements FileServer.Receiver {
        final Target target;
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
            Request request = createMetaRequest(fileData);
            invokeRpcIfValidConnection(request);
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

        // non-private for testing
        static Request createMetaRequest(FileReferenceData fileData) {
            Request request = new Request(FileReceiver.RECEIVE_META_METHOD);
            request.parameters().add(new StringValue(fileData.fileReference().value()));
            request.parameters().add(new StringValue(fileData.filename()));
            request.parameters().add(new StringValue(fileData.type().name()));
            request.parameters().add(new Int64Value(fileData.size()));
            // Only add paramter if not gzip, this is default and old clients will not handle the extra parameter
            if (fileData.compressionType() != CompressionType.gzip)
                request.parameters().add(new StringValue(fileData.compressionType().name()));
            return request;
        }

        private void sendPart(int session, FileReference ref, int partId, byte [] buf) {
            Request request = new Request(FileReceiver.RECEIVE_PART_METHOD);
            request.parameters().add(new StringValue(ref.value()));
            request.parameters().add(new Int32Value(session));
            request.parameters().add(new Int32Value(partId));
            request.parameters().add(new DataValue(buf));
            invokeRpcIfValidConnection(request);
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
            invokeRpcIfValidConnection(request);
            if (request.isError()) {
                throw new IllegalArgumentException("Failed delivering reference '" + fileData.fileReference().value() + "' with file '" + fileData.filename() + "' to " +
                                                           target.toString() + " with error: '" + request.errorMessage() + "'.");
            } else {
                if (request.returnValues().get(0).asInt32() != 0) {
                    throw new IllegalArgumentException("Unknown error from target '" + target.toString() + "' during rpc call " + request.methodName());
                }
            }
        }

        private void invokeRpcIfValidConnection(Request request) {
            if (target.isValid()) {
                target.invokeSync(request, Duration.ofMinutes(10));
            } else {
                throw new RuntimeException("Connection to " + target + " is invalid", target.getConnectionLostReason());
            }
        }
    }

    private void serveFile(Request request) {
        request.detach();
        rpcAuthorizer.authorizeFileRequest(request)
                .thenRun(() -> { // okay to do in authorizer thread as serveFile is async
                    FileServer.Receiver receiver = new ChunkedFileReceiver(request.target());

                    FileReference reference = new FileReference(request.parameters().get(0).asString());
                    boolean downloadFromOtherSourceIfNotFound = request.parameters().get(1).asInt32() == 0;
                    Set<FileReferenceData.CompressionType> acceptedCompressionTypes = Set.of(CompressionType.gzip);
                    // Newer clients specify accepted compression types in request
                    // TODO Require acceptedCompressionTypes parameter in Vespa 9
                    if (request.parameters().size() > 2)
                        acceptedCompressionTypes = Arrays.stream(request.parameters().get(2).asStringArray())
                                                         .map(CompressionType::valueOf)
                                                         .collect(Collectors.toSet());
                    log.log(Level.FINE, "acceptedCompressionTypes=" + acceptedCompressionTypes);

                    fileServer.serveFile(reference, downloadFromOtherSourceIfNotFound, acceptedCompressionTypes, request, receiver);
                });
    }

    private void setFileReferencesToDownload(Request req) {
        req.detach();
        rpcAuthorizer.authorizeFileRequest(req)
                .thenRun(() -> { // okay to do in authorizer thread as downloadIfNeeded is async
                    String[] fileReferenceStrings = req.parameters().get(0).asStringArray();
                    Stream.of(fileReferenceStrings)
                            .map(FileReference::new)
                            .forEach(fileReference -> downloader.downloadIfNeeded(
                                    new FileReferenceDownload(fileReference,
                                                              req.target().toString(),
                                                              false /* downloadFromOtherSourceIfNotFound */)));
                    req.returnValues().add(new Int32Value(0));
                });
    }
}
