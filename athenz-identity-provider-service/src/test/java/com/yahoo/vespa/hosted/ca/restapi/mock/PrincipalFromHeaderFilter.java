// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi.mock;

import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.jdisc.http.server.jetty.RequestUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzService;

import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * Read principal from http header
 *
 * @author mortent
 */
public class PrincipalFromHeaderFilter implements SecurityRequestFilter {

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        String principal = request.getHeader("PRINCIPAL");
        request.setUserPrincipal(new AthenzPrincipal(new AthenzService(principal)));

        Optional<String> certificate = Optional.ofNullable(request.getHeader("CERTIFICATE"));
        certificate.ifPresent(cert -> {
            var x509cert = X509CertificateUtils.fromPem(StringUtilities.unescape(cert));
            request.setAttribute(RequestUtils.JDISC_REQUEST_X509CERT, new X509Certificate[]{x509cert});
        });
    }
}
