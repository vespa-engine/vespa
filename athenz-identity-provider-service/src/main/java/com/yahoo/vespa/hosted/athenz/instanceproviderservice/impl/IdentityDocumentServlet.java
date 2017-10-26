// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import com.yahoo.log.LogLevel;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
public class IdentityDocumentServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger(IdentityDocumentServlet.class.getName());

    private final IdentityDocumentGenerator identityDocumentGenerator;

    public IdentityDocumentServlet(IdentityDocumentGenerator identityDocumentGenerator) {
        this.identityDocumentGenerator = identityDocumentGenerator;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // TODO verify tls client cert
        String hostname = req.getParameter("hostname");
        if (hostname == null) {
            String message = "The 'hostname' parameter is missing";
            log.log(LogLevel.ERROR, message);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
            return;
        }
        try {
            log.log(LogLevel.INFO, "Generating identity document for " + hostname);
            String signedIdentityDocument = identityDocumentGenerator.generateSignedIdentityDocument(hostname);
            resp.setContentType("application/json");
            PrintWriter writer = resp.getWriter();
            writer.print(signedIdentityDocument);
            writer.flush();
        } catch (Exception e) {
            String message = String.format("Unable to generate identity doument [%s]", e.getMessage());
            log.log(LogLevel.ERROR, message);
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, message);
        }
    }

}
