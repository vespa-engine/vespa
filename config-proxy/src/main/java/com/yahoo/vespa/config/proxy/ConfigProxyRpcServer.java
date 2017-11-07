// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.FileReference;
import com.yahoo.jrt.*;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.ErrorCode;
import com.yahoo.vespa.config.protocol.JRTConfigRequestFactory;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * An RPC server that handles config and file distribution requests.
 *
 * @author hmusum
 */
// TODO: Rename now that it also support file distribution request
public class ConfigProxyRpcServer implements Runnable, TargetWatcher, RpcServer {

    private final static Logger log = Logger.getLogger(ConfigProxyRpcServer.class.getName());
    private static final int TRACELEVEL = 6;

    private final Spec spec;
    private final Supervisor supervisor = new Supervisor(new Transport());
    private final ProxyServer proxyServer;

    ConfigProxyRpcServer(ProxyServer proxyServer, Spec spec) {
        this.proxyServer = proxyServer;
        this.spec = spec;
        declareConfigMethods();
        declareFileDistributionMethods();
    }

    public void run() {
        try {
            Acceptor acceptor = supervisor.listen(spec);
            log.log(LogLevel.DEBUG, "Ready for requests on " + spec);
            supervisor.transport().join();
            acceptor.shutdown().join();
        } catch (ListenFailedException e) {
            proxyServer.stop();
            throw new RuntimeException("Could not listen on " + spec, e);
        }
    }

    void shutdown() {
        supervisor.transport().shutdown();
    }

    Spec getSpec() {
        return spec;
    }

    private void declareConfigMethods() {
        supervisor.addMethod(JRTMethods.createConfigV3GetConfigMethod(this, "getConfigV3"));
        supervisor.addMethod(new Method("ping", "", "i",
                this, "ping")
                .methodDesc("ping")
                .returnDesc(0, "ret code", "return code, 0 is OK"));
        supervisor.addMethod(new Method("printStatistics", "", "s",
                this, "printStatistics")
                .methodDesc("printStatistics")
                .returnDesc(0, "statistics", "Statistics for server"));
        supervisor.addMethod(new Method("listCachedConfig", "", "S",
                this, "listCachedConfig")
                .methodDesc("list cached configs)")
                .returnDesc(0, "data", "string array of configs"));
        supervisor.addMethod(new Method("listCachedConfigFull", "", "S",
                this, "listCachedConfigFull")
                .methodDesc("list cached configs with cache content)")
                .returnDesc(0, "data", "string array of configs"));
        supervisor.addMethod(new Method("listSourceConnections", "", "S",
                this, "listSourceConnections")
                .methodDesc("list config source connections)")
                .returnDesc(0, "data", "string array of source connections"));
        supervisor.addMethod(new Method("invalidateCache", "", "S",
                this, "invalidateCache")
                .methodDesc("list config source connections)")
                .returnDesc(0, "data", "0 if success, 1 otherwise"));
        supervisor.addMethod(new Method("updateSources", "s", "s",
                this, "updateSources")
                .methodDesc("update list of config sources")
                .returnDesc(0, "ret", "list of updated config sources"));
        supervisor.addMethod(new Method("setMode", "s", "S",
                this, "setMode")
                .methodDesc("Set config proxy mode { default | memorycache }")
                .returnDesc(0, "ret", "0 if success, 1 otherwise as first element, description as second element"));
        supervisor.addMethod(new Method("getMode", "", "s",
                this, "getMode")
                .methodDesc("What serving mode the config proxy is in (default, memorycache)")
                .returnDesc(0, "ret", "mode as a string"));
        supervisor.addMethod(new Method("dumpCache", "s", "s",
                this, "dumpCache")
                .methodDesc("Dump cache to disk")
                .paramDesc(0, "path", "path to write cache contents to")
                .returnDesc(0, "ret", "Empty string or error message"));
    }

    private void declareFileDistributionMethods() {
        // Legacy method, needs to be the same name as used in filedistributor
        supervisor.addMethod(new Method("waitFor", "s", "s",
                this, "getFile")
                .methodDesc("get path to file reference")
                .paramDesc(0, "file reference", "file reference")
                .returnDesc(0, "path", "path to file"));
        supervisor.addMethod(new Method("filedistribution.getFile", "s", "s",
                this, "getFile")
                .methodDesc("get path to file reference")
                .paramDesc(0, "file reference", "file reference")
                .returnDesc(0, "path", "path to file"));
        supervisor.addMethod(new Method("filedistribution.getActiveFileReferencesStatus", "", "SD",
                this, "getActiveFileReferencesStatus")
                .methodDesc("download status for file references")
                .returnDesc(0, "file references", "array of file references")
                .returnDesc(1, "download status", "percentage downloaded of each file reference in above array"));
        supervisor.addMethod(new Method("filedistribution.setFileReferencesToDownload", "S", "i",
                this, "setFileReferencesToDownload")
                .methodDesc("set which file references to download")
                .paramDesc(0, "file references", "file reference to download")
                .returnDesc(0, "ret", "0 if success, 1 otherwise"));
        supervisor.addMethod(new Method("filedistribution.receiveFile", "ssxlis", "i", // TODO Temporary method to get started with testing
                this, "receiveFile")
                .methodDesc("receive file reference content")
                .paramDesc(0, "file references", "file reference to download")
                .paramDesc(1, "filename", "filename")
                .paramDesc(2, "content", "array of bytes")
                .paramDesc(3, "hash", "xx64hash of the file content")
                .paramDesc(4, "errorcode", "Error code. 0 if none")
                .paramDesc(5, "error-description", "Error description.")
                .returnDesc(0, "ret", "0 if success, 1 otherwise"));
    }

    //---------------- RPC methods ------------------------------------

    /**
     * Handles RPC method "config.v3.getConfig" requests.
     *
     * @param req a Request
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public final void getConfigV3(Request req) {
        log.log(LogLevel.SPAM, () -> "getConfigV3");
        JRTServerConfigRequest request = JRTServerConfigRequestV3.createFromRequest(req);
        if (isProtocolVersionSupported(request)) {
            preHandle(req);
            getConfigImpl(request);
        }
    }

    /**
     * Returns 0 if server is alive.
     *
     * @param req a Request
     */
    public final void ping(Request req) {
        req.returnValues().add(new Int32Value(0));
    }

    /**
     * Returns a String with statistics data for the server.
     *
     * @param req a Request
     */
    public final void printStatistics(Request req) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nDelayed responses queue size: ");
        sb.append(proxyServer.delayedResponses.size());
        sb.append("\nContents: ");
        for (DelayedResponse delayed : proxyServer.delayedResponses.responses()) {
            sb.append(delayed.getRequest().toString()).append("\n");
        }

        req.returnValues().add(new StringValue(sb.toString()));
    }

    public final void listCachedConfig(Request req) {
        listCachedConfig(req, false);
    }

    public final void listCachedConfigFull(Request req) {
        listCachedConfig(req, true);
    }

    public final void listSourceConnections(Request req) {
        String[] ret = new String[2];
        ret[0] = "Current source: " + proxyServer.getActiveSourceConnection();
        ret[1] = "All sources:\n" + printSourceConnections();
        req.returnValues().add(new StringArray(ret));
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void updateSources(Request req) {
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
    }

    public final void invalidateCache(Request req) {
        proxyServer.getMemoryCache().clear();
        String[] s = new String[2];
        s[0] = "0";
        s[1] = "success";
        req.returnValues().add(new StringArray(s));
    }

    public final void setMode(Request req) {
        String suppliedMode = req.parameters().get(0).asString();
        log.log(LogLevel.DEBUG, () -> "Supplied mode=" + suppliedMode);
        String[] s = new String[2];
        if (Mode.validModeName(suppliedMode.toLowerCase())) {
            proxyServer.setMode(suppliedMode);
            s[0] = "0";
            s[1] = "success";
        } else {
            s[0] = "1";
            s[1] = "Could not set mode to '" + suppliedMode + "'. Legal modes are '" + Mode.modes() + "'";
        }

        req.returnValues().add(new StringArray(s));
    }

    public final void getMode(Request req) {
        req.returnValues().add(new StringValue(proxyServer.getMode().name()));
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void dumpCache(Request req) {
        final MemoryCache memoryCache = proxyServer.getMemoryCache();
        req.returnValues().add(new StringValue(memoryCache.dumpCacheToDisk(req.parameters().get(0).asString(), memoryCache)));
    }

    // TODO: Duplicate of code in FileAcquirereImpl. Find out where to put it. What about C++ code using this RPC call?
    private static final int baseErrorCode = 0x10000;
    private static final int baseFileProviderErrorCode = baseErrorCode + 0x1000;

    private static final int fileReferenceDoesNotExists = baseFileProviderErrorCode;
    private static final int fileReferenceRemoved = fileReferenceDoesNotExists + 1;
    private static final int fileReferenceInternalError = fileReferenceRemoved + 1;

    @SuppressWarnings({"UnusedDeclaration"})
    public final void getFile(Request req) {
        req.detach();
        FileReference fileReference = new FileReference(req.parameters().get(0).asString());
        log.log(LogLevel.DEBUG, "getFile() called for file reference '" + fileReference.value() + "'");
        Optional<File> pathToFile = proxyServer.fileDownloader().getFile(fileReference);
        try {
            if (pathToFile.isPresent()) {
                req.returnValues().add(new StringValue(pathToFile.get().getAbsolutePath()));
                log.log(LogLevel.INFO, "File reference '" + fileReference.value() + "' available at " + pathToFile.get());
            } else {
                log.log(LogLevel.INFO, "File reference '" + fileReference.value() + "' not found, returning error");
                req.setError(fileReferenceDoesNotExists, "File reference '" + fileReference.value() + "' not found");
            }
        } catch (Throwable e) {
            log.log(LogLevel.WARNING, "File reference '" + fileReference.value() + "' got exeption: " + e.getMessage());
            req.setError(fileReferenceInternalError, "File reference '" + fileReference.value() + "' removed");
        }
        req.returnRequest();
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void getActiveFileReferencesStatus(Request req) {
        Map<FileReference, Double> downloadStatus = proxyServer.fileDownloader().downloadStatus();

        String[] fileRefArray = new String[downloadStatus.keySet().size()];
        fileRefArray = downloadStatus.keySet().stream()
                .map(FileReference::value)
                .collect(Collectors.toList())
                .toArray(fileRefArray);

        double[] downloadStatusArray = new double[downloadStatus.values().size()];
        int i = 0;
        for (Double d : downloadStatus.values()) {
            downloadStatusArray[i++] = d;
        }

        req.returnValues().add(new StringArray(fileRefArray));
        req.returnValues().add(new DoubleArray(downloadStatusArray));
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void setFileReferencesToDownload(Request req) {
        String[] fileReferenceStrings = req.parameters().get(0).asStringArray();
        List<FileReference> fileReferences = Stream.of(fileReferenceStrings)
                .map(FileReference::new)
                .collect(Collectors.toList());
        proxyServer.fileDownloader().queueForDownload(fileReferences);

        req.returnValues().add(new Int32Value(0));
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void receiveFile(Request req) {
        FileReference fileReference = new FileReference(req.parameters().get(0).asString());
        String filename = req.parameters().get(1).asString();
        byte[] content = req.parameters().get(2).asData();
        proxyServer.fileDownloader().receiveFile(fileReference, filename, content);
        req.returnValues().add(new Int32Value(0));
    }

    //----------------------------------------------------

    private boolean isProtocolVersionSupported(JRTServerConfigRequest request) {
        Set<Long> supportedProtocolVersions = JRTConfigRequestFactory.supportedProtocolVersions();
        if (supportedProtocolVersions.contains(request.getProtocolVersion())) {
            return true;
        } else {
            String message = "Illegal protocol version " + request.getProtocolVersion() +
                    " in request " + request.getShortDescription() + ", only protocol versions " + supportedProtocolVersions + " are supported";
            log.log(LogLevel.ERROR, message);
            request.addErrorResponse(ErrorCode.ILLEGAL_PROTOCOL_VERSION, message);
        }
        return false;
    }

    private void preHandle(Request req) {
        proxyServer.getStatistics().incRpcRequests();
        req.detach();
        req.target().addWatcher(this);
    }

    /**
     * Handles all versions of "getConfig" requests.
     *
     * @param request a Request
     */
    private void getConfigImpl(JRTServerConfigRequest request) {
        request.getRequestTrace().trace(TRACELEVEL, "Config proxy getConfig()");
        log.log(LogLevel.DEBUG, () ->"getConfig: " + request.getShortDescription() + ",configmd5=" + request.getRequestConfigMd5());
        if (!request.validateParameters()) {
            // Error code is set in verifyParameters if parameters are not OK.
            log.log(LogLevel.WARNING, "Parameters for request " + request + " did not validate: " + request.errorCode() + " : " + request.errorMessage());
            returnErrorResponse(request, request.errorCode(), "Parameters for request " + request.getShortDescription() + " did not validate: " + request.errorMessage());
            return;
        }
        try {
            RawConfig config = proxyServer.resolveConfig(request);
            if (config == null) {
                log.log(LogLevel.SPAM, () -> "No config received yet for " + request.getShortDescription() + ", not sending response");
            } else if (ProxyServer.configOrGenerationHasChanged(config, request)) {
                returnOkResponse(request, config);
            } else {
                log.log(LogLevel.SPAM, "No new config for " + request.getShortDescription() + ", not sending response");
            }
        } catch (Exception e) {
            e.printStackTrace();
            returnErrorResponse(request, com.yahoo.vespa.config.ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    private String printSourceConnections() {
        StringBuilder sb = new StringBuilder();
        for (String s : proxyServer.getSourceConnections()) {
            sb.append(s).append("\n");
        }
        return sb.toString();
    }

    final void listCachedConfig(Request req, boolean full) {
        String[] ret;
        MemoryCache cache = proxyServer.getMemoryCache();
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
            sb.append(config.getConfigMd5());
            if (full) {
                sb.append(",");
                sb.append(config.getPayload());
            }
            ret[i] = sb.toString();
            i++;
        }
        Arrays.sort(ret);
        req.returnValues().add(new StringArray(ret));
    }

    /**
     * Removes invalid targets (closed client connections) from delayedResponsesQueue.
     *
     * @param target a Target that has become invalid (i.e, client has closed connection)
     */
    @Override
    public void notifyTargetInvalid(Target target) {
        log.log(LogLevel.DEBUG, () -> "Target invalid " + target);
        for (Iterator<DelayedResponse> it = proxyServer.delayedResponses.responses().iterator(); it.hasNext(); ) {
            DelayedResponse delayed = it.next();
            JRTServerConfigRequest request = delayed.getRequest();
            if (request.getRequest().target().equals(target)) {
                log.log(LogLevel.DEBUG, () -> "Removing " + request.getShortDescription());
                it.remove();
            }
        }
        // TODO: Could we also cancel active getConfig requests upstream if the client was the only one
        // requesting this config?
    }

    public void returnOkResponse(JRTServerConfigRequest request, RawConfig config) {
        request.getRequestTrace().trace(TRACELEVEL, "Config proxy returnOkResponse()");
        request.addOkResponse(config.getPayload(), config.getGeneration(), config.getConfigMd5());
        log.log(LogLevel.DEBUG, () -> "Return response: " + request.getShortDescription() + ",configMd5=" + config.getConfigMd5() +
                ",generation=" + config.getGeneration());
        log.log(LogLevel.SPAM, () -> "Config payload in response for " + request.getShortDescription() + ":" + config.getPayload());


        // TODO Catch exception for now, since the request might have been returned in CheckDelayedResponse
        // TODO Move logic so that all requests are returned in CheckDelayedResponse
        try {
            request.getRequest().returnRequest();
        } catch (IllegalStateException e) {
            log.log(LogLevel.DEBUG, () -> "Something bad happened when sending response for '" + request.getShortDescription() + "':" + e.getMessage());
        }
    }

    public void returnErrorResponse(JRTServerConfigRequest request, int errorCode, String message) {
        request.getRequestTrace().trace(TRACELEVEL, "Config proxy returnErrorResponse()");
        request.addErrorResponse(errorCode, message);
        request.getRequest().returnRequest();
    }
}
