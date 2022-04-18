// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.TargetWatcher;
import com.yahoo.vespa.config.JRTMethods;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.ErrorCode.INTERNAL_ERROR;

/**
 * An RPC server that handles config and file distribution requests.
 *
 * @author hmusum
 */
public class ConfigProxyRpcServer implements Runnable, TargetWatcher {

    private final static Logger log = Logger.getLogger(ConfigProxyRpcServer.class.getName());
    static final int TRACELEVEL = 6;

    private final Spec spec;
    private final Supervisor supervisor;
    private final ProxyServer proxyServer;
    private final ExecutorService rpcExecutor = Executors.newFixedThreadPool(8);

    ConfigProxyRpcServer(ProxyServer proxyServer, Supervisor supervisor, Spec spec) {
        this.proxyServer = proxyServer;
        this.spec = spec;
        this.supervisor = supervisor;
        declareConfigMethods();
    }

    public void run() {
        try {
            Acceptor acceptor = supervisor.listen(spec);
            log.log(Level.FINE, () -> "Ready for requests on " + spec);
            supervisor.transport().join();
            acceptor.shutdown().join();
        } catch (ListenFailedException e) {
            proxyServer.stop();
            throw new RuntimeException("Could not listen on " + spec, e);
        }
    }

    void shutdown() {
        try {
            rpcExecutor.shutdownNow();
            rpcExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        supervisor.transport().shutdown().join();
    }

    Spec getSpec() {
        return spec;
    }

    private void declareConfigMethods() {
        supervisor.addMethod(JRTMethods.createConfigV3GetConfigMethod(this::getConfigV3));
        supervisor.addMethod(new Method("ping", "", "i",
                this::ping)
                .methodDesc("ping")
                .returnDesc(0, "ret code", "return code, 0 is OK"));
        supervisor.addMethod(new Method("listCachedConfig", "", "S",
                this::listCachedConfig)
                .methodDesc("list cached configs)")
                .returnDesc(0, "data", "string array of configs"));
        supervisor.addMethod(new Method("listCachedConfigFull", "", "S",
                this::listCachedConfigFull)
                .methodDesc("list cached configs with cache content)")
                .returnDesc(0, "data", "string array of configs"));
        supervisor.addMethod(new Method("listSourceConnections", "", "S",
                this::listSourceConnections)
                .methodDesc("list config source connections)")
                .returnDesc(0, "data", "string array of source connections"));
        supervisor.addMethod(new Method("invalidateCache", "", "S",
                this::invalidateCache)
                .methodDesc("list config source connections)")
                .returnDesc(0, "data", "0 if success, 1 otherwise"));
        supervisor.addMethod(new Method("updateSources", "s", "s",
                this::updateSources)
                .methodDesc("update list of config sources")
                .returnDesc(0, "ret", "list of updated config sources"));
        supervisor.addMethod(new Method("setMode", "s", "S",
                this::setMode)
                .methodDesc("Set config proxy mode { default | memorycache }")
                .returnDesc(0, "ret", "0 if success, 1 otherwise as first element, description as second element"));
        supervisor.addMethod(new Method("getMode", "", "s",
                this::getMode)
                .methodDesc("What serving mode the config proxy is in (default, memorycache)")
                .returnDesc(0, "ret", "mode as a string"));
        supervisor.addMethod(new Method("dumpCache", "s", "s",
                this::dumpCache)
                .methodDesc("Dump cache to disk")
                .paramDesc(0, "path", "path to write cache contents to")
                .returnDesc(0, "ret", "Empty string or error message"));
    }

    //---------------- RPC methods ------------------------------------

    /**
     * Handles RPC method "config.v3.getConfig" requests.
     *
     * @param req a Request
     */
    private void getConfigV3(Request req) {
        dispatchRpcRequest(req, () -> {
            JRTServerConfigRequest request = JRTServerConfigRequestV3.createFromRequest(req);
            req.target().addWatcher(this);
            getConfigImpl(request);
        });
    }

    /**
     * Returns 0 if server is alive.
     *
     * @param req a Request
     */
    private void ping(Request req) {
        dispatchRpcRequest(req, () -> {
            req.returnValues().add(new Int32Value(0));
            req.returnRequest();
        });
    }

    private void listCachedConfig(Request req) {
        dispatchRpcRequest(req, () -> listCachedConfig(req, false));
    }

    private void listCachedConfigFull(Request req) {
        dispatchRpcRequest(req, () -> listCachedConfig(req, true));
    }

    private void listSourceConnections(Request req) {
        dispatchRpcRequest(req, () -> {
            String[] ret = new String[2];
            ret[0] = "Current source: " + proxyServer.getActiveSourceConnection();
            ret[1] = "All sources:\n" + printSourceConnections();
            req.returnValues().add(new StringArray(ret));
            req.returnRequest();
        });
    }

    private void updateSources(Request req) {
        dispatchRpcRequest(req, () -> {
            String sources = req.parameters().get(0).asString();
            String ret;
            System.out.println(proxyServer.getMode());
            if (proxyServer.getMode().requiresConfigSource()) {
                proxyServer.updateSourceConnections(Arrays.asList(sources.split(",")));
                ret = "Updated config sources to: " + sources;
            } else {
                ret = "Cannot update sources when in '" + proxyServer.getMode().name() + "' mode";
            }
            req.returnValues().add(new StringValue(ret));
            req.returnRequest();
        });
    }

    private void invalidateCache(Request req) {
        dispatchRpcRequest(req, () -> {
            proxyServer.memoryCache().clear();
            String[] s = new String[2];
            s[0] = "0";
            s[1] = "success";
            req.returnValues().add(new StringArray(s));
            req.returnRequest();
        });
    }

    private void setMode(Request req) {
        dispatchRpcRequest(req, () -> {
            String suppliedMode = req.parameters().get(0).asString();
            String[] s = new String[2];
            try {
                proxyServer.setMode(suppliedMode);
                s[0] = "0";
                s[1] = "success";
            } catch (Exception e) {
                s[0] = "1";
                s[1] = e.getMessage();
            }

            req.returnValues().add(new StringArray(s));
            req.returnRequest();
        });
    }

    private void getMode(Request req) {
        dispatchRpcRequest(req, () -> {
            req.returnValues().add(new StringValue(proxyServer.getMode().name()));
            req.returnRequest();
        });
    }

    private void dumpCache(Request req) {
        dispatchRpcRequest(req, () -> {
            final MemoryCache memoryCache = proxyServer.memoryCache();
            req.returnValues().add(new StringValue(memoryCache.dumpCacheToDisk(req.parameters().get(0).asString(), memoryCache)));
            req.returnRequest();
        });
    }

    //----------------------------------------------------

    private void dispatchRpcRequest(Request request, Runnable handler) {
        request.detach();
        log.log(Level.FINEST, () -> String.format("Dispatching RPC request %s", requestLogId(request)));
        rpcExecutor.execute(() -> {
            try {
                log.log(Level.FINEST, () -> String.format("Executing RPC request %s.", requestLogId(request)));
                handler.run();
            } catch (Exception e) {
                log.log(Level.WARNING,
                        String.format("Exception thrown during execution of RPC request %s: %s", requestLogId(request), e.getMessage()), e);
            }
        });
    }

    private String requestLogId(Request request) {
        return String.format("%s/%08X", request.methodName(), request.hashCode());
    }

    /**
     * Handles all versions of "getConfig" requests.
     *
     * @param request a Request
     */
    private void getConfigImpl(JRTServerConfigRequest request) {
        ResponseHandler responseHandler = new ResponseHandler();
        request.getRequestTrace().trace(TRACELEVEL, "Config proxy getConfig()");
        log.log(Level.FINE, () ->"getConfig: " + request);
        if (!request.validateParameters()) {
            // Error code is set in validateParameters if parameters are not OK.
            log.log(Level.WARNING, "Invalid parameters for request " + request + ": " + request.errorCode() + " : " + request.errorMessage());
            responseHandler.returnErrorResponse(request, request.errorCode(), "Invalid parameters for request " + request +
                    ": " + request.errorMessage());
            return;
        }

        try {
            Optional<RawConfig> config = proxyServer.resolveConfig(request);
            if (config.isEmpty())
                log.log(Level.FINEST, () -> "No config received yet for " + request + ", not sending response");
            else if (ProxyServer.configOrGenerationHasChanged(config.get(), request))
                responseHandler.returnOkResponse(request, config.get());
            else
                log.log(Level.FINEST, () -> "No new config for " + request + ", not sending response");
        } catch (Exception e) {
            log.log(Level.WARNING, "Resolving config " + request + " failed", e);
            responseHandler.returnErrorResponse(request, INTERNAL_ERROR, e.getMessage());
        }
    }

    private String printSourceConnections() {
        StringBuilder sb = new StringBuilder();
        for (String s : proxyServer.getSourceConnections()) {
            sb.append(s).append("\n");
        }
        return sb.toString();
    }

    private void listCachedConfig(Request req, boolean full) {
        String[] ret;
        MemoryCache cache = proxyServer.memoryCache();
        ret = new String[cache.size()];
        int i = 0;
        for (RawConfig config : cache.values()) {
            StringBuilder sb = new StringBuilder();
            sb.append(config.getNamespace());
            sb.append(".");
            sb.append(config.getName());
            sb.append(",");
            sb.append(config.getConfigId());
            sb.append(",");
            sb.append(config.getGeneration());
            sb.append(",");
            sb.append(config.getPayloadChecksums());
            if (full) {
                sb.append(",");
                sb.append(config.getPayload());
            }
            ret[i] = sb.toString();
            i++;
        }
        Arrays.sort(ret);
        req.returnValues().add(new StringArray(ret));
        req.returnRequest();
    }

    /**
     * Removes invalid targets (closed client connections) from delayedResponsesQueue.
     *
     * @param target a Target that has become invalid (i.e, client has closed connection)
     */
    @Override
    public void notifyTargetInvalid(Target target) {
        log.log(Level.FINE, () -> "Target invalid " + target);
        for (Iterator<DelayedResponse> it = proxyServer.delayedResponses().responses().iterator(); it.hasNext(); ) {
            DelayedResponse delayed = it.next();
            JRTServerConfigRequest request = delayed.getRequest();
            if (request.getRequest().target().equals(target)) {
                log.log(Level.FINE, () -> "Removing " + request.getShortDescription());
                it.remove();
            }
        }
        // TODO: Could we also cancel active getConfig requests upstream if the client was the only one
        // requesting this config?
    }

}
