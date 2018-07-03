// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.gui;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.searchlib.rankingexpression.parser.RankingExpressionParserTokenManager;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.statuspage.StatusPageClient;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;


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
        com.yahoo.vespa.hosted.controller.restapi.Path path = new com.yahoo.vespa.hosted.controller.restapi.Path(request.getUri().getPath());
        if (path.matches("/querybuilder/")) {
            return new FileResponse("_includes/index.html");
        }
        if (!path.matches("/querybuilder/{*}") ) {
            return ErrorResponse.notFoundError("Nothing at " + path);
        }
        String filepath = path.getRest();
        if (!isValidPath(GUIHandler.class.getClassLoader().getResource("static").getFile()+"/"+filepath)){
            return ErrorResponse.notFoundError("Nothing at " + path);
        }
        return new FileResponse(filepath);
    }

    private static boolean isValidPath(String path) {
        File file = new File(path);
        return file.exists();
    }

    private static class FileResponse extends HttpResponse {

        private final Path path;

        public FileResponse(String relativePath) {
            super(200);
            this.path = Paths.get(GUIHandler.class.getClassLoader().getResource("static").getFile(), relativePath);
        }

        @Override
        public void render(OutputStream out) throws IOException {
            byte[] data = Files.readAllBytes(path);
            out.write(data);
        }

        @Override
        public String getContentType() {
            System.out.println("HELE PATH: "+path.toString());
            if (path.toString().endsWith(".css")) {
                return "text/css";
            } else if (path.toString().endsWith(".js")) {
                return "application/javascript";
            } else if (path.toString().endsWith(".html")) {
                return "text/html";
            }else if (path.toString().endsWith(".php")) {
                return "text/php";
            }else if (path.toString().endsWith(".svg")) {
                return "image/svg+xml";
            }else if (path.toString().endsWith(".eot")) {
                return "application/vnd.ms-fontobject";
            }else if (path.toString().endsWith(".ttf")) {
                return "font/ttf";
            }else if (path.toString().endsWith(".woff")) {
                return "font/woff";
            }else if (path.toString().endsWith(".woff2")) {
                return "font/woff2";
            }else if (path.toString().endsWith(".otf")) {
                return "font/otf";
            }else if (path.toString().endsWith(".png")) {
                return "image/png";
            }else if (path.toString().endsWith(".xml")) {
                return "application/xml";
            }else if (path.toString().endsWith(".ico")) {
                return "image/x-icon";
            }else if (path.toString().endsWith(".json")) {
                return "application/json";
            }else if (path.toString().endsWith(".ttf")) {
                return "font/ttf";
            }

            return "text/html";
        }
    }

}
