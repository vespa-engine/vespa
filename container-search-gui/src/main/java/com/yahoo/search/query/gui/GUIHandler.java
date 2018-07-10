// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.gui;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.search.query.restapi.ErrorResponse;
import com.yahoo.yolean.Exceptions;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;

/**
 * Takes requests on /querybuilder
 *
 * @author  Henrik Høiness
 */

public class GUIHandler extends LoggingRequestHandler {

    @Inject
    public GUIHandler(Context parentCtx) {
        super(parentCtx);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handleGET(HttpRequest request) {
        com.yahoo.restapi.Path path = new com.yahoo.restapi.Path(request.getUri().getPath());
        if (path.matches("/querybuilder/")) {
            return new FileResponse("_includes/index.html");
        }
        if (!path.matches("/querybuilder/{*}") ) {
            return ErrorResponse.notFoundError("Nothing at path:" + path);
        }
        String filepath = path.getRest();
        if (!isValidPath(filepath)){
            return ErrorResponse.notFoundError("Nothing at path:" + filepath);
        }
        return new FileResponse(filepath);
    }

    private static boolean isValidPath(String path) {
        InputStream in = GUIHandler.class.getClassLoader().getResourceAsStream("gui/"+path);
        boolean isValid = (in != null);
        if(isValid){
            try { in.close(); } catch (IOException e) {/* Problem with closing inputstream */}
        }

        return isValid;
    }

    private static class FileResponse extends HttpResponse {

        private final String path;

        public FileResponse(String relativePath) {
            super(200);
            this.path = relativePath;
        }

        @Override
        public void render(OutputStream out) throws IOException {
            InputStream is = GUIHandler.class.getClassLoader().getResourceAsStream("gui/"+this.path);
            byte[] buf = new byte[1024];
            int numRead;
            while ( (numRead = is.read(buf) ) >= 0) {
                out.write(buf, 0, numRead);
            }
        }

        @Override
        public String getContentType() {
            if (path.endsWith(".css")) {
                return "text/css";
            } else if (path.endsWith(".js")) {
                return "application/javascript";
            } else if (path.endsWith(".html")) {
                return "text/html";
            }else if (path.endsWith(".php")) {
                return "text/php";
            }else if (path.endsWith(".svg")) {
                return "image/svg+xml";
            }else if (path.endsWith(".eot")) {
                return "application/vnd.ms-fontobject";
            }else if (path.endsWith(".ttf")) {
                return "font/ttf";
            }else if (path.endsWith(".woff")) {
                return "font/woff";
            }else if (path.endsWith(".woff2")) {
                return "font/woff2";
            }else if (path.endsWith(".otf")) {
                return "font/otf";
            }else if (path.endsWith(".png")) {
                return "image/png";
            }else if (path.endsWith(".xml")) {
                return "application/xml";
            }else if (path.endsWith(".ico")) {
                return "image/x-icon";
            }else if (path.endsWith(".json")) {
                return "application/json";
            }else if (path.endsWith(".ttf")) {
                return "font/ttf";
            }
            return "text/html";
        }
    }

}
