// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca;

import com.google.inject.Inject;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.logging.Logger;

/**
 * @author bjorncs
 * @author freva
 */
@Path("/sign")
public class CertificateSignerResource {

    private static final Logger log = Logger.getLogger(CertificateSignerResource.class.getName());

    private final CertificateSigner certificateSigner;

    @Inject
    public CertificateSignerResource(@Component CertificateSigner certificateSigner) {
        this.certificateSigner = certificateSigner;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public CertificateSerializedPayload generateCertificate(CsrSerializedPayload csrPayload,
                                                            @Context HttpServletRequest req) {
        try {
            InetAddress addr = InetAddress.getByName(req.getRemoteAddr());
            String remoteHostname = addr.getHostName();
            Pkcs10Csr csr = csrPayload.csr;
            log.log(LogLevel.DEBUG, "Certification request from " + remoteHostname + ": " + csr);
            X509Certificate certificate = certificateSigner.generateX509Certificate(csr, remoteHostname);
            return new CertificateSerializedPayload(certificate);
        } catch (IllegalArgumentException e) {
            log.log(LogLevel.WARNING, e.getMessage());
            throw new ForbiddenException(e.getMessage(), e);
        } catch (RuntimeException e) {
            log.log(LogLevel.ERROR, e.getMessage(), e);
            throw new InternalServerErrorException(e.getMessage(), e);
        } catch (UnknownHostException e) {
            String message = "Failed to resolve remote address " + req.getRemoteAddr() +
                    ", must resolve to match value in Common Name";
            log.log(LogLevel.ERROR, message);
            throw new BadRequestException(message);
        }
    }
}
