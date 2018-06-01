// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.jrt.Request;
import com.yahoo.log.LogLevel;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ErrorCode;
import com.yahoo.vespa.config.UnknownConfigIdException;
import com.yahoo.vespa.config.protocol.*;
import com.yahoo.vespa.config.server.GetConfigContext;
import com.yahoo.vespa.config.server.UnknownConfigDefinitionException;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
* @author hmusum
*/
class GetConfigProcessor implements Runnable {

    private static final Logger log = Logger.getLogger(GetConfigProcessor.class.getName());
    private static final String localHostName = HostName.getLocalhost();

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
        final Request req = request.getRequest();
        if (req.isError()) {
            Level logLevel = (req.errorCode() == ErrorCode.APPLICATION_NOT_LOADED) ? LogLevel.DEBUG : LogLevel.INFO;
            log.log(logLevel, logPre + req.errorMessage());
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
    public void run() {
        //Request has already been detached
        if ( ! request.validateParameters()) {
            // Error code is set in verifyParameters if parameters are not OK.
            log.log(LogLevel.WARNING, "Parameters for request " + request + " did not validate: " + request.errorCode() + " : " + request.errorMessage());
            respond(request);
            return;
        }
        Trace trace = request.getRequestTrace();
        if (logDebug(trace)) {
            debugLog(trace, "GetConfigProcessor.run() on " + localHostName);
        }

        Optional<TenantName> tenant = rpcServer.resolveTenant(request, trace);

        // If we are certain that this request is from a node that no longer belongs to this application,
        // fabricate an empty request to cause the sentinel to stop all running services
        if (rpcServer.isHostedVespa() && rpcServer.allTenantsLoaded() && !tenant.isPresent() && isSentinelConfigRequest(request)) {
            returnEmpty(request);
            return;
        }

        GetConfigContext context = rpcServer.createGetConfigContext(tenant, request, trace);
        if (context == null || ! context.requestHandler().hasApplication(context.applicationId(), Optional.<Version>empty())) {
            handleError(request, ErrorCode.APPLICATION_NOT_LOADED, "No application exists");
            return;
        }

        Optional<Version> vespaVersion = rpcServer.useRequestVersion() ?
                request.getVespaVersion().map(VespaVersion::toString).map(Version::fromString) :
                Optional.empty();
        if (logDebug(trace)) {
            debugLog(trace, "Using version " + getPrintableVespaVersion(vespaVersion));
        }

        if ( ! context.requestHandler().hasApplication(context.applicationId(), vespaVersion)) {
            handleError(request, ErrorCode.UNKNOWN_VESPA_VERSION, "Unknown Vespa version in request: " + getPrintableVespaVersion(vespaVersion));
            return;
        }

        this.logPre = TenantRepository.logPre(context.applicationId());
        ConfigResponse config;
        try {
            config = rpcServer.resolveConfig(request, context, vespaVersion);
        } catch (UnknownConfigDefinitionException e) {
            handleError(request, ErrorCode.UNKNOWN_DEFINITION, "Unknown config definition " + request.getConfigKey());
            return;
        } catch (UnknownConfigIdException e) {
            handleError(request, ErrorCode.ILLEGAL_CONFIGID, "Illegal config id " + request.getConfigKey().getConfigId());
            return;
        } catch (Throwable e) {
            log.log(Level.SEVERE, "Unexpected error handling config request", e);
            handleError(request, ErrorCode.INTERNAL_ERROR, "Internal error " + e.getMessage());
            return;
        }

        // config == null is not an error, but indicates that the config will be returned later.
        if ((config != null) && (!config.hasEqualConfig(request) || config.hasNewerGeneration(request) || forceResponse)) {
            // debugLog(trace, "config response before encoding:" + config.toString());
            request.addOkResponse(request.payloadFromResponse(config), config.getGeneration(), config.isInternalRedeploy(), config.getConfigMd5());
            if (logDebug(trace)) {
                debugLog(trace, "return response: " + request.getShortDescription());
            }
            respond(request);
        } else {
            if (logDebug(trace)) {
                debugLog(trace, "delaying response " + request.getShortDescription());
            }
            rpcServer.delayResponse(request, context);
        }
    }

    private boolean isSentinelConfigRequest(JRTServerConfigRequest request) {
        return request.getConfigKey().getName().equals(SentinelConfig.getDefName()) &&
               request.getConfigKey().getNamespace().equals(SentinelConfig.getDefNamespace());
    }

    private static String getPrintableVespaVersion(Optional<Version> vespaVersion) {
        return (vespaVersion.isPresent() ? vespaVersion.get().toString() : "LATEST");
    }

    private void returnEmpty(JRTServerConfigRequest request) {
        ConfigPayload emptyPayload = ConfigPayload.empty();
        String configMd5 = ConfigUtils.getMd5(emptyPayload);
        ConfigResponse config = SlimeConfigResponse.fromConfigPayload(emptyPayload, null, 0, false, configMd5);
        request.addOkResponse(request.payloadFromResponse(config), config.getGeneration(), false, config.getConfigMd5());
        respond(request);
    }

    static boolean logDebug(Trace trace) {
        return trace.shouldTrace(RpcServer.TRACELEVEL_DEBUG) || log.isLoggable(LogLevel.DEBUG);
    }

    private void debugLog(Trace trace, String message) {
        if (logDebug(trace)) {
            log.log(LogLevel.DEBUG, logPre + message);
            trace.trace(RpcServer.TRACELEVEL_DEBUG, logPre + message);
        }
    }

}
