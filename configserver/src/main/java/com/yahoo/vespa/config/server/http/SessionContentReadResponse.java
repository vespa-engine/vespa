// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.container.jdisc.HttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.yahoo.jdisc.http.HttpResponse.Status.OK;

/**
 * Represents a response for a request to read contents of a file.
 *
 * @author Ulf Lilleengen
 */
public class SessionContentReadResponse extends HttpResponse {

    private static final Map<String, String> contentTypeByExtension = loadContentTypeByExtension();

    private final ApplicationFile file;

    public SessionContentReadResponse(ApplicationFile file) {
        super(OK);
        this.file = file;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        try (InputStream inputStream = file.createInputStream()) {
            inputStream.transferTo(outputStream);
        }
    }

    @Override
    public String getContentType() {
        String filename = file.getPath().getName();
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex >= 0) {
            String contentType = contentTypeByExtension.get(filename.substring(lastDotIndex + 1));
            if (contentType != null) return contentType;
        }
        return DEFAULT_MIME_TYPE;
    }

    private static Map<String, String> loadContentTypeByExtension() {
        ClassLoader classLoader = SessionContentReadResponse.class.getClassLoader();
        Pattern whitespace = Pattern.compile("\\s");
        Map<String, String> map = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(classLoader.getResourceAsStream("mime.types")))) {
            while (reader.ready()) {
                String line = reader.readLine();
                if (line.isEmpty() || line.charAt(0) == '#') continue;

                String[] parts = whitespace.split(line);
                for (int i = 1; i < parts.length; i++)
                    map.putIfAbsent(parts[i], parts[0]);
            }

            return map;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
