// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc.security;

import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.FileReference;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.security.NodeIdentifier;
import com.yahoo.config.provision.security.NodeIdentifierException;
import com.yahoo.config.provision.security.NodeIdentity;
import com.yahoo.jrt.Request;
import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TransportSecurityUtils;
import com.yahoo.security.tls.ConnectionAuthContext;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.rpc.RequestHandlerProvider;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.rpc.security.AuthorizationException.Type;
import static com.yahoo.yolean.Exceptions.throwUnchecked;

/**
 * A {@link RpcAuthorizer} that perform access control for configserver RPC methods when TLS and multi-tenant mode are enabled.
 *
 * @author bjorncs
 */
public class MultiTenantRpcAuthorizer implements RpcAuthorizer {

    private static final Logger log = Logger.getLogger(MultiTenantRpcAuthorizer.class.getName());

    private final NodeIdentifier nodeIdentifier;
    private final HostRegistry hostRegistry;
    private final RequestHandlerProvider handlerProvider;
    private final Executor executor;

    public MultiTenantRpcAuthorizer(NodeIdentifier nodeIdentifier,
                                    HostRegistry hostRegistry,
                                    RequestHandlerProvider handlerProvider,
                                    int threadPoolSize) {
        this(nodeIdentifier,
             hostRegistry,
             handlerProvider,
             Executors.newFixedThreadPool(threadPoolSize, new DaemonThreadFactory("multi-tenant-rpc-authorizer-")));
    }

    MultiTenantRpcAuthorizer(NodeIdentifier nodeIdentifier,
                             HostRegistry hostRegistry,
                             RequestHandlerProvider handlerProvider,
                             Executor executor) {
        this.nodeIdentifier = nodeIdentifier;
        this.hostRegistry = hostRegistry;
        this.handlerProvider = handlerProvider;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> authorizeConfigRequest(Request request) {
        return doAsyncAuthorization(request, this::doConfigRequestAuthorization);
    }

    @Override
    public CompletableFuture<Void> authorizeFileRequest(Request request) {
        return doAsyncAuthorization(request, this::doFileRequestAuthorization);
    }

    private CompletableFuture<Void> doAsyncAuthorization(Request request, BiConsumer<Request, NodeIdentity> authorizer) {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        getPeerIdentity(request)
                                .ifPresent(peerIdentity -> authorizer.accept(request, peerIdentity));
                        log.log(Level.FINE, () -> String.format("Authorization succeeded for request '%s' from '%s'",
                                                                   request.methodName(), request.target().toString()));
                    } catch (Throwable t) {
                        handleAuthorizationFailure(request, t);
                    }
                },
                executor);
    }

    private void doConfigRequestAuthorization(Request request, NodeIdentity peerIdentity) {
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
                    ApplicationId applicationId = hostRegistry.getApplicationId(hostname);
                    if (applicationId == null) {
                        if (isConfigKeyForSentinelConfig(configKey)) {
                            return; // config processor will return empty sentinel config for unknown nodes
                        }
                        throw new AuthorizationException(Type.SILENT, String.format("Host '%s' not found in host registry for [%s]", hostname, configKey));
                    }
                    RequestHandler tenantHandler = getTenantHandler(applicationId.tenant());
                    ApplicationId resolvedApplication = tenantHandler.resolveApplicationId(hostname);
                    ApplicationId peerOwner = applicationId(peerIdentity);
                    if (peerOwner.equals(resolvedApplication)) {
                        return; // allowed to access
                    }
                    throw new AuthorizationException(
                            String.format(
                                    "Peer is not allowed to access config owned by %s. Peer is owned by %s",
                                    resolvedApplication.toShortString(), peerOwner.toShortString()));
                }
            default:
                throw new AuthorizationException(String.format("'%s' nodes are not allowed to access config", peerIdentity.nodeType()));
        }
    }

    private void doFileRequestAuthorization(Request request, NodeIdentity peerIdentity) {
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
                throw new AuthorizationException(
                        String.format("Peer is not allowed to access file reference %s. Peer is owned by %s. File references owned by this application: %s",
                                      requestedFile.value(), peerOwner.toShortString(), filesOwnedByApplication));
            default:
                throw new AuthorizationException(String.format("'%s' nodes are not allowed to access files", peerIdentity.nodeType()));
        }
    }

    private void handleAuthorizationFailure(Request request, Throwable throwable) {
        boolean isAuthorizationException = throwable instanceof AuthorizationException;
        String errorMessage = String.format("For request '%s' from '%s': %s", request.methodName(), request.target().toString(), throwable.getMessage());
        if (!isAuthorizationException || ((AuthorizationException) throwable).type() != Type.SILENT) {
            log.log(Level.INFO, errorMessage);
        }
        log.log(Level.FINE, throwable, throwable::getMessage);
        JrtErrorCode error = isAuthorizationException ? JrtErrorCode.UNAUTHORIZED : JrtErrorCode.AUTHORIZATION_FAILED;
        request.setError(error.code, errorMessage);
        request.returnRequest();
        throw throwUnchecked(throwable); // rethrow exception to ensure that subsequent completion stages are not executed (don't execute implementation of rpc method).
    }

    // TODO Make peer identity mandatory once TLS mixed mode is removed
    private Optional<NodeIdentity> getPeerIdentity(Request request) {
        ConnectionAuthContext authCtx = request.target().connectionAuthContext();
        if (authCtx.peerCertificate().isEmpty()) {
            if (TransportSecurityUtils.getInsecureMixedMode() == MixedMode.DISABLED) {
                throw new IllegalStateException("Security context missing"); // security context should always be present
            }
            return Optional.empty(); // client choose to communicate over insecure channel
        }
        List<X509Certificate> certChain = authCtx.peerCertificateChain();
        if (certChain.isEmpty()) {
            throw new IllegalStateException("Client authentication is not enforced!"); // clients should be required to authenticate when TLS is enabled
        }
        try {
            NodeIdentity identity = nodeIdentifier.identifyNode(certChain);
            log.log(Level.FINE, () -> String.format("Client '%s' identified as %s", request.target().toString(), identity.toString()));
            return Optional.of(identity);
        } catch (NodeIdentifierException e) {
            throw new AuthorizationException("Failed to identify peer: " + e.getMessage(), e);
        }
    }

    private static boolean isConfigKeyForGlobalConfig(ConfigKey<?> configKey) {
        return "*".equals(configKey.getConfigId());
    }

    private static boolean isConfigKeyForSentinelConfig(ConfigKey<?> configKey) {
        return SentinelConfig.getDefName().equals(configKey.getName())
                && SentinelConfig.getDefNamespace().equals(configKey.getNamespace());
    }

    private static ApplicationId applicationId(NodeIdentity peerIdentity) {
        return peerIdentity.applicationId()
                .orElseThrow(() -> new AuthorizationException("Peer node is not associated with an application: " + peerIdentity.toString()));
    }

    private RequestHandler getTenantHandler(TenantName tenantName) {
        return handlerProvider.getRequestHandler(tenantName)
                .orElseThrow(() -> new AuthorizationException(String.format("No handler exists for tenant '%s'", tenantName.value())));
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
