// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca.model.CertificateSerializedPayload;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca.model.CsrSerializedPayload;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
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
            String remoteHostname = getRemoteHostname(req);
            CsrSerializedPayload csrSerializedPayload = Utils.getMapper().readValue(req.getReader(), CsrSerializedPayload.class);

            PKCS10CertificationRequest csr = getPKCS10CertRequest(new StringReader(csrSerializedPayload.csr));
            log.log(LogLevel.DEBUG, "Certification request from " + remoteHostname + ": " + csr);

            X509Certificate certificate = certificateSigner.generateX509Certificate(csr, remoteHostname);
            CertificateSerializedPayload certificateSerializedPayload = new CertificateSerializedPayload(x509CertificateToString(certificate));

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/json");
            resp.getWriter().write(Utils.getMapper().writeValueAsString(certificateSerializedPayload));
        } catch (RuntimeException e) {
            log.log(LogLevel.ERROR, e.getMessage(), e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    private String getRemoteHostname(HttpServletRequest req) {
        return req.getRemoteHost();
    }

    private static PKCS10CertificationRequest getPKCS10CertRequest(Reader csrReader) {
        try (PEMParser pemParser = new PEMParser(csrReader)) {
            return (PKCS10CertificationRequest) pemParser.readObject();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse CSR", e);
        }
    }

    private static String x509CertificateToString(X509Certificate cert) {
        try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
            pemWriter.flush();
            return stringWriter.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert X509Certificate to PEM format", e);
        }
    }
}
