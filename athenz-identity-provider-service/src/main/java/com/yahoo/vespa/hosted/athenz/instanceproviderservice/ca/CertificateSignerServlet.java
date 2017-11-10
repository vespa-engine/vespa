// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca.model.CertificateSerializedPayload;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca.model.CsrSerializedPayload;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.logging.Logger;

/**
 * @author freva
 */
public class CertificateSignerServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger(CertificateSignerServlet.class.getName());

    private final CertificateSigner certificateSigner;

    public CertificateSignerServlet(CertificateSigner certificateSigner) {
        this.certificateSigner = certificateSigner;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String remoteHostname = req.getRemoteHost();
            PKCS10CertificationRequest csr = Utils.getMapper().readValue(req.getReader(), CsrSerializedPayload.class).csr;

            log.log(LogLevel.DEBUG, "Certification request from " + remoteHostname + ": " + csr);

            X509Certificate certificate = certificateSigner.generateX509Certificate(csr, remoteHostname);
            CertificateSerializedPayload certificateSerializedPayload = new CertificateSerializedPayload(certificate);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/json");
            resp.getWriter().write(Utils.getMapper().writeValueAsString(certificateSerializedPayload));
        } catch (RuntimeException e) {
            log.log(LogLevel.ERROR, e.getMessage(), e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }
}
