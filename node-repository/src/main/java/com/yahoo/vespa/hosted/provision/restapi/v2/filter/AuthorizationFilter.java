// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.jdisc.http.servlet.ServletRequest;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.restapi.v2.Authorizer;
import com.yahoo.vespa.hosted.provision.restapi.v2.ErrorResponse;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.Principal;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.logging.Logger;

/**
 * Authorization filter for all paths in config server.
 *
 * @author mpolden
 */
public class AuthorizationFilter implements SecurityRequestFilter {

    private static final Logger log = Logger.getLogger(AuthorizationFilter.class.getName());

    private final BiPredicate<Principal, URI> authorizer;
    private final BiConsumer<ErrorResponse, ResponseHandler> responseWriter;

    @Inject
    public AuthorizationFilter(Zone zone, NodeRepository nodeRepository) {
         this(new Authorizer(zone.system(), nodeRepository), AuthorizationFilter::log); // TODO: Use write method once all clients are using certificates
    }

    AuthorizationFilter(BiPredicate<Principal, URI> authorizer,
                        BiConsumer<ErrorResponse, ResponseHandler> responseWriter) {
        this.authorizer = authorizer;
        this.responseWriter = responseWriter;
    }

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        Optional<X509Certificate> cert = certificateFrom(request);
        if (cert.isPresent()) {
            if (!authorizer.test(() -> commonName(cert.get()), request.getUri())) {
                responseWriter.accept(ErrorResponse.forbidden(
                        String.format("%s %s denied for %s: Invalid credentials", request.getMethod(),
                                      request.getUri().getPath(), request.getRemoteAddr())), handler
                );
            }
        } else {
            responseWriter.accept(ErrorResponse.unauthorized(
                    String.format("%s %s denied for %s: Missing credentials", request.getMethod(),
                                  request.getUri().getPath(), request.getRemoteAddr())), handler
            );
        }
    }

    /** Write error response */
    static void write(ErrorResponse response, ResponseHandler handler) {
        try (FastContentWriter writer = ResponseDispatch.newInstance(response.getJdiscResponse())
                                                        .connectFastWriter(handler)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                response.render(out);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            writer.write(out.toByteArray());
        }
    }

    /** Log error response without writing anything */
    private static void log(ErrorResponse response, @SuppressWarnings("unused") ResponseHandler handler) {
        log.warning("Would reject request: " + response.getStatus() + " - " + response.message());
    }

    /** Read common name (CN) from certificate */
    private static String commonName(X509Certificate certificate) {
        try {
            X500Name subject = new JcaX509CertificateHolder(certificate).getSubject();
            RDN cn = subject.getRDNs(BCStyle.CN)[0];
            return IETFUtils.valueToString(cn.getFirst().getValue());
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get client certificate from request */
    private static Optional<X509Certificate> certificateFrom(DiscFilterRequest request) {
        Object x509cert = request.getAttribute(ServletRequest.JDISC_REQUEST_X509CERT);
        return Optional.ofNullable(x509cert)
                       .filter(X509Certificate[].class::isInstance)
                       .map(X509Certificate[].class::cast)
                       .filter(certs -> certs.length > 0)
                       .map(certs -> certs[0]);
    }

}
