// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.InstanceConfirmation;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A Servlet implementing the Athenz Service Provider InstanceConfirmation API
 *
 * @author bjorncs
 */
public class InstanceConfirmationServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger(InstanceConfirmationServlet.class.getName());

    private final InstanceValidator instanceValidator;

    public InstanceConfirmationServlet(InstanceValidator instanceValidator) {
        this.instanceValidator = instanceValidator;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // TODO Validate that request originates from ZTS
        try {
            String confirmationContent = toString(req.getReader());
            log.log(LogLevel.DEBUG, () -> "Confirmation content: " + confirmationContent);
            InstanceConfirmation instanceConfirmation =
                    Utils.getMapper().readValue(confirmationContent, InstanceConfirmation.class);
            log.log(LogLevel.DEBUG, () -> "Parsed confirmation content: " + instanceConfirmation.toString());
            if (!instanceValidator.isValidInstance(instanceConfirmation)) {
                String message = "Invalid instance: " + instanceConfirmation;
                log.log(LogLevel.ERROR, message);
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, message);
            } else {
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setContentType("application/json");
                resp.getWriter().write(Utils.getMapper().writeValueAsString(instanceConfirmation));
            }
        } catch (JsonParseException | JsonMappingException e) {
            String message = "InstanceConfirmation is not valid JSON";
            log.log(LogLevel.ERROR, message, e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
        }
    }

    private static String toString(Reader reader) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            return bufferedReader.lines().collect(Collectors.joining("\n"));
        }
    }

}
