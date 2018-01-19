// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;

import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Requests for content and content status, both for prepared and active sessions,
 * are handled by this class.
 *
 * @author hmusum
 */
public class ContentHandler {

    public HttpResponse get(ContentRequest request) {
        ContentRequest.ReturnType returnType = request.getReturnType();
        String urlBase = request.getUrlBase("/content/");
        if (ContentRequest.ReturnType.STATUS.equals(returnType)) {
            return status(request, urlBase);
        } else {
            return content(request, urlBase);
        }
    }

    public HttpResponse put(ContentRequest request) {
        ApplicationFile file = request.getFile();
        if (request.getPath().endsWith("/")) {
            createDirectory(request, file);
        } else {
            createFile(request, file);
        }
        return createResponse(request);
    }

    public HttpResponse delete(ContentRequest request) {
        ApplicationFile file = request.getExistingFile();
        deleteFile(file);
        return createResponse(request);
    }

    private HttpResponse content(ContentRequest request, String urlBase) {
        ApplicationFile file = request.getExistingFile();
        if (file.isDirectory()) {
            return new SessionContentListResponse(urlBase, listSortedFiles(file, request.getPath(), request.isRecursive()));
        }
        return new SessionContentReadResponse(file);
    }

    private HttpResponse status(ContentRequest request, String urlBase) {
        ApplicationFile file = request.getFile();
        if (file.isDirectory()) {
            return new SessionContentStatusListResponse(urlBase, listSortedFiles(file, request.getPath(), request.isRecursive()));
        }
        return new SessionContentStatusResponse(file, urlBase);
    }

    private static List<ApplicationFile> listSortedFiles(ApplicationFile file, String path, boolean recursive) {
        if (!path.isEmpty() && !path.endsWith("/")) {
            return Arrays.asList(file);
        }
        List<ApplicationFile> files = file.listFiles(recursive);
        Collections.sort(files);
        return files;
    }

    private void createFile(ContentRequest request, ApplicationFile file) {
        if ( ! request.hasRequestBody()) {
            throw new BadRequestException("Request must contain body when creating a file");
        }
        try {
            file.writeFile(new InputStreamReader(request.getData()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private void createDirectory(ContentRequest request, ApplicationFile file) {
        if (request.hasRequestBody()) {
            // TODO: Enable when we have a good way to check if request contains a body
            // return new HttpErrorResponse(HttpResponse.Status.BAD_REQUEST, "Request should not contain a body when creating directories");
        }
        file.createDirectory();
    }

    private void deleteFile(ApplicationFile file) {
        try {
            file.delete();
        } catch (RuntimeException e) {
            throw new BadRequestException("File '" + file.getPath() + "' is not an empty directory");
        }
    }

    private HttpResponse createResponse(ContentRequest request) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("prepared", request.getUrlBase("/prepared"));
        return new SessionResponse(slime, root);
    }
}
