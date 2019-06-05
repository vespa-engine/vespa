// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc.security;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.FileReference;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.security.NodeIdentifier;
import com.yahoo.config.provision.security.NodeIdentifierException;
import com.yahoo.config.provision.security.NodeIdentity;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.SecurityContext;
import com.yahoo.log.LogLevel;
import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TransportSecurityUtils;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.rpc.RequestHandlerProvider;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;


/**
 * A {@link RpcAuthorizer} that perform access control for configserver RPC methods when TLS and multi-tenant mode are enabled.
 *
 * @author bjorncs
 */
public class MultiTenantRpcAuthorizer implements RpcAuthorizer {

    public enum Mode { LOG_ONLY, ENFORCE }

    private static final Logger log = Logger.getLogger(MultiTenantRpcAuthorizer.class.getName());

    private final NodeIdentifier nodeIdentifier;
    private final HostRegistry<TenantName> hostRegistry;
    private final RequestHandlerProvider handlerProvider;
    private final Executor executor;
    private final Mode mode;

    public MultiTenantRpcAuthorizer(NodeIdentifier nodeIdentifier,
                                    HostRegistries hostRegistries,
                                    RequestHandlerProvider handlerProvider,
                                    Mode mode,
                                    int threadPoolSize) {
        this(nodeIdentifier,
             hostRegistries.getTenantHostRegistry(),
             handlerProvider,
             Executors.newFixedThreadPool(threadPoolSize, new DaemonThreadFactory("multi-tenant-rpc-authorizer-")),
             mode);
    }

    MultiTenantRpcAuthorizer(NodeIdentifier nodeIdentifier,
                             HostRegistry<TenantName> hostRegistry,
                             RequestHandlerProvider handlerProvider,
                             Executor executor,
                             Mode mode) {
        this.nodeIdentifier = nodeIdentifier;
        this.hostRegistry = hostRegistry;
        this.handlerProvider = handlerProvider;
        this.executor = executor;
        this.mode = mode;
    }

    @Override
    public CompletableFuture<Void> authorizeConfigRequest(Request request) {
        return CompletableFuture.runAsync(() -> doConfigRequestAuthorization(request), executor);
    }

    @Override
    public CompletableFuture<Void> authorizeFileRequest(Request request) {
        return CompletableFuture.runAsync(() -> doFileRequestAuthorization(request), executor);
    }

    private void doConfigRequestAuthorization(Request request) {
        try {
            getPeerIdentity(request)
                    .ifPresent(peerIdentity -> {
                        switch (peerIdentity.nodeType()) {
                            case config:
                                return; // configserver is allowed to access all config
                            case proxy:
                            case tenant:
                            case host:
                                JRTServerConfigRequestV3 configRequest = JRTServerConfigRequestV3.createFromRequest(request);
                                ConfigKey<?> configKey = configRequest.getConfigKey();
                                if (isConfigKeyForGlobalConfig(configKey)) {
                                    GlobalConfigAuthorizationPolicy.verifyAccessAllowed(configKey, peerIdentity.nodeType());
                                    return; // global config access ok
                                } else {
                                    String hostname = configRequest.getClientHostName();
                                    TenantName tenantName = Optional.ofNullable(hostRegistry.getKeyForHost(hostname))
                                            .orElseThrow(() -> new AuthorizationException(String.format("Host '%s' not found in host registry", hostname)));
                                    RequestHandler tenantHandler = getTenantHandler(tenantName);
                                    ApplicationId resolvedApplication = tenantHandler.resolveApplicationId(hostname);
                                    ApplicationId peerOwner = applicationId(peerIdentity);
                                    if (peerOwner.equals(resolvedApplication)) {
                                        return; // allowed to access
                                    }
                                    throw new AuthorizationException(
                                            String.format(
                                                    "Peer is not allowed to access config for owned by %s. Peer is owned by %s",
                                                    resolvedApplication.toShortString(), peerOwner.toShortString()));
                                }
                            default:
                                throw new AuthorizationException(String.format("'%s' nodes are not allowed to access config", peerIdentity.nodeType()));
                        }
                    });
            log.log(LogLevel.DEBUG, () -> String.format("Authorization succeeded for request '%s' from '%s'",
                                                        request.methodName(), request.target().toString()));
        } catch (Throwable t) {
            handleAuthorizationFailure(request, t);
        }
    }

    private void doFileRequestAuthorization(Request request) {
        try {
            getPeerIdentity(request)
                    .ifPresent(peerIdentity -> {
                        switch (peerIdentity.nodeType()) {
                            case config:
                                return; // configserver is allowed to access all files
                            case proxy:
                            case tenant:
                            case host:
                                ApplicationId peerOwner = applicationId(peerIdentity);
                                FileReference requestedFile = new FileReference(request.parameters().get(0).asString());
                                RequestHandler tenantHandler = getTenantHandler(peerOwner.tenant());
                                Set<FileReference> filesOwnedByApplication = tenantHandler.listFileReferences(peerOwner);
                                if (filesOwnedByApplication.contains(requestedFile)) {
                                    return; // allowed to access
                                }
                                throw new AuthorizationException("Peer is not allowed to access file " + requestedFile.value());
                            default:
                                throw new AuthorizationException(String.format("'%s' nodes are not allowed to access files", peerIdentity.nodeType()));
                        }
                    });
            log.log(LogLevel.DEBUG, () -> String.format("Authorization succeeded for request '%s' from '%s'",
                                                        request.methodName(), request.target().toString()));
        } catch (Throwable t) {
            handleAuthorizationFailure(request, t);
        }
    }

    private void handleAuthorizationFailure(Request request, Throwable throwable) {
        String errorMessage = String.format("For request '%s' from '%s': %s", request.methodName(), request.target().toString(), throwable.getMessage());
        log.log(LogLevel.WARNING, errorMessage);
        log.log(LogLevel.DEBUG, throwable, throwable::getMessage);
        if (mode == Mode.ENFORCE) {
            JrtErrorCode error = throwable instanceof AuthorizationException ? JrtErrorCode.UNAUTHORIZED : JrtErrorCode.AUTHORIZATION_FAILED;
            request.setError(error.code, errorMessage);
            request.returnRequest();
            throwUnchecked(throwable); // rethrow exception to ensure that subsequent completion stages are not executed (don't execute implementation of rpc method).
        }
    }

    // TODO Make peer identity mandatory once TLS mixed mode is removed
    private Optional<NodeIdentity> getPeerIdentity(Request request) {
        Optional<SecurityContext> securityContext = request.target().getSecurityContext();
        if (securityContext.isEmpty()) {
            if (TransportSecurityUtils.getInsecureMixedMode() == MixedMode.DISABLED) {
                throw new IllegalStateException("Security context missing"); // security context should always be present
            }
            return Optional.empty(); // client choose to communicate over insecure channel
        }
        List<X509Certificate> certChain = securityContext.get().peerCertificateChain();
        if (certChain.isEmpty()) {
            throw new IllegalStateException("Client authentication is not enforced!"); // clients should be required to authenticate when TLS is enabled
        }
        try {
            NodeIdentity identity = nodeIdentifier.identifyNode(certChain);
            log.log(LogLevel.DEBUG, () -> String.format("Client '%s' identified as %s", request.target().toString(), identity.toString()));
            return Optional.of(identity);
        } catch (NodeIdentifierException e) {
            throw new AuthorizationException("Failed to identity peer: " + e.getMessage(), e);
        }
    }

    private static boolean isConfigKeyForGlobalConfig(ConfigKey<?> configKey) {
        return "*".equals(configKey.getConfigId());
    }

    private static ApplicationId applicationId(NodeIdentity peerIdentity) {
        return peerIdentity.applicationId()
                .orElseThrow(() -> new AuthorizationException("Peer node is not associated with an application: " + peerIdentity.toString()));
    }

    private RequestHandler getTenantHandler(TenantName tenantName) {
        return handlerProvider.getRequestHandler(tenantName)
                .orElseThrow(() -> new AuthorizationException(String.format("No handler exists for tenant '%s'", tenantName.value())));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable t) throws T {
        throw (T)t;
    }

    private enum JrtErrorCode {
        UNAUTHORIZED(1),
        AUTHORIZATION_FAILED(2);

        final int code;

        JrtErrorCode(int errorOffset) {
            this.code = 0x20000 + errorOffset;
        }
    }

}
