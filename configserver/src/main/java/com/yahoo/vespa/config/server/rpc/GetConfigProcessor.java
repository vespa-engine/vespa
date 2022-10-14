// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.di.config.ApplicationBundlesConfig;
import com.yahoo.jrt.Request;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ErrorCode;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.vespa.config.UnknownConfigIdException;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.vespa.config.protocol.VespaVersion;
import com.yahoo.vespa.config.server.GetConfigContext;
import com.yahoo.vespa.config.server.UnknownConfigDefinitionException;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.ErrorCode.APPLICATION_NOT_LOADED;
import static com.yahoo.vespa.config.ErrorCode.UNKNOWN_VESPA_VERSION;
import static com.yahoo.vespa.config.protocol.SlimeConfigResponse.fromConfigPayload;

/**
 * @author hmusum
 */
class GetConfigProcessor implements Runnable {

    private static final Logger log = Logger.getLogger(GetConfigProcessor.class.getName());
    private static final String localHostName = HostName.getLocalhost();

    private static final PayloadChecksums emptyApplicationBundlesConfigChecksums =
            PayloadChecksums.fromPayload(Payload.from(ConfigPayload.fromInstance(new ApplicationBundlesConfig.Builder().build())));

    private final JRTServerConfigRequest request;

    /* True only when this request has expired its server timeout and we need to respond to the client */
    private final boolean forceResponse;
    private final RpcServer rpcServer;
    private String logPre = "";

    GetConfigProcessor(RpcServer rpcServer, JRTServerConfigRequest request, boolean forceResponse) {
        this.rpcServer = rpcServer;
        this.request = request;
        this.forceResponse = forceResponse;
    }

    private void respond(JRTServerConfigRequest request) {
        Request req = request.getRequest();
        if (req.isError()) {
            Level logLevel = Set.of(APPLICATION_NOT_LOADED, UNKNOWN_VESPA_VERSION).contains(req.errorCode()) ? Level.FINE : Level.INFO;
            log.log(logLevel, () -> logPre + req.errorMessage());
        }
        rpcServer.respond(request);
    }

    private void handleError(JRTServerConfigRequest request, int errorCode, String message) {
        String target = "(unknown)";
        try {
            target = request.getRequest().target().toString();
        } catch (IllegalStateException e) {
            //ignore when no target
        }
        request.addErrorResponse(errorCode, logPre + "Failed request (" + message + ") from " + target);
        respond(request);
    }

    // TODO: Increment statistics (Metrics) failed counters when requests fail
    public Pair<GetConfigContext, Long> getConfig(JRTServerConfigRequest request) {
        // Request has already been detached
        if ( ! request.validateParameters()) {
            // Error code is set in verifyParameters if parameters are not OK.
            log.log(Level.WARNING, "Parameters for request " + request + " did not validate: " + request.errorCode() + " : " + request.errorMessage());
            respond(request);
            return null;
        }
        Trace trace = request.getRequestTrace();
        debugLog(trace, "GetConfigProcessor.run() on " + localHostName);

        Optional<TenantName> tenant = rpcServer.resolveTenant(request, trace);

        // If we are certain that this request is from a node that no longer belongs to this application,
        // fabricate an empty request to cause the sentinel to stop all running services
        if (rpcServer.canReturnEmptySentinelConfig() && rpcServer.allTenantsLoaded() && tenant.isEmpty() && isSentinelConfigRequest(request)) {
            returnEmpty(request);
            return null;
        }

        GetConfigContext context = rpcServer.createGetConfigContext(tenant, request, trace);
        if (context == null || ! context.requestHandler().hasApplication(context.applicationId(), Optional.empty())) {
            handleError(request, APPLICATION_NOT_LOADED, "No application exists");
            return null;
        }
        logPre = TenantRepository.logPre(context.applicationId());

        Optional<Version> vespaVersion = rpcServer.useRequestVersion() ?
                request.getVespaVersion().map(VespaVersion::toString).map(Version::fromString) :
                Optional.empty();
        debugLog(trace, "Using version " + printableVespaVersion(vespaVersion));

        if ( ! context.requestHandler().hasApplication(context.applicationId(), vespaVersion)) {
            handleError(request, ErrorCode.UNKNOWN_VESPA_VERSION, "Unknown Vespa version in request: " + printableVespaVersion(vespaVersion));
            return null;
        }

        ConfigResponse config;
        try {
            config = rpcServer.resolveConfig(request, context, vespaVersion);
        } catch (UnknownConfigDefinitionException e) {
            handleError(request, ErrorCode.UNKNOWN_DEFINITION, "Unknown config definition " + request.getConfigKey());
            return null;
        } catch (UnknownConfigIdException e) {
            handleError(request, ErrorCode.ILLEGAL_CONFIGID, "Illegal config id " + request.getConfigKey().getConfigId());
            return null;
        } catch (Throwable e) {
            log.log(Level.SEVERE, "Unexpected error handling config request", e);
            handleError(request, ErrorCode.INTERNAL_ERROR, "Internal error " + e.getMessage());
            return null;
        }

        // config == null is not an error, but indicates that the config will be returned later.
        if ((config != null) && (    ! config.getPayloadChecksums().matches(request.getRequestConfigChecksums())
                                  ||   config.hasNewerGeneration(request)
                                  ||   forceResponse)) {
            if (     ApplicationBundlesConfig.class.equals(request.getConfigKey().getConfigClass())  // If it's a Java container ...
                && ! context.requestHandler().compatibleWith(vespaVersion, context.applicationId())  // ... with a runtime version incompatible with the deploying version ...
                && ! emptyApplicationBundlesConfigChecksums.matches(config.getPayloadChecksums())) { // ... and there actually are incompatible user bundles, then return no config:
                handleError(request, ErrorCode.INCOMPATIBLE_VESPA_VERSION, "Version " + printableVespaVersion(vespaVersion) + " is binary incompatible with the latest deployed version");
                return null;
            }

            // debugLog(trace, "config response before encoding:" + config.toString());
            request.addOkResponse(request.payloadFromResponse(config), config.getGeneration(), config.applyOnRestart(), config.getPayloadChecksums());
            debugLog(trace, "return response: " + request.getShortDescription());
            respond(request);
        } else {
            debugLog(trace, "delaying response " + request.getShortDescription());
            return new Pair<>(context, config != null ? config.getGeneration() : 0);
        }
        return null;
    }

    @Override
    public void run() {
        Pair<GetConfigContext, Long> delayed = getConfig(request);

        if (delayed != null) {
            rpcServer.delayResponse(request, delayed.getFirst());
            if (rpcServer.hasNewerGeneration(delayed.getFirst().applicationId(), delayed.getSecond())) {
                // This will ensure that if the config activation train left the station while I was boarding,
                // another train will immediately be scheduled.
                rpcServer.configActivated(delayed.getFirst().applicationId());
            }
        }
    }

    private boolean isSentinelConfigRequest(JRTServerConfigRequest request) {
        return request.getConfigKey().getName().equals(SentinelConfig.getDefName()) &&
               request.getConfigKey().getNamespace().equals(SentinelConfig.getDefNamespace());
    }

    private static String printableVespaVersion(Optional<Version> vespaVersion) {
        return vespaVersion.map(Version::toFullString).orElse("LATEST");
    }

    private void returnEmpty(JRTServerConfigRequest request) {
        log.log(Level.FINE, () -> "Returning empty sentinel config for request from " + request.getClientHostName());
        var emptyPayload = ConfigPayload.fromInstance(new SentinelConfig.Builder().build());
        ConfigResponse config = fromConfigPayload(emptyPayload,
                                                  0,
                                                  false,
                                                  PayloadChecksums.fromPayload(Payload.from(emptyPayload)));
        request.addOkResponse(request.payloadFromResponse(config), config.getGeneration(), false, config.getPayloadChecksums());
        respond(request);
    }

    static boolean logDebug(Trace trace) {
        return trace.shouldTrace(RpcServer.TRACELEVEL_DEBUG) || log.isLoggable(Level.FINE);
    }

    private void debugLog(Trace trace, String message) {
        if (logDebug(trace)) {
            log.log(Level.FINE, () -> logPre + message);
            trace.trace(RpcServer.TRACELEVEL_DEBUG, logPre + message);
        }
    }

}
