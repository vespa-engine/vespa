// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.vespa.hosted.controller.api.application.v4.EnvironmentResource;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.fileupload.ParameterParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides reading a multipart/form-data request type into a map of bytes for each part,
 * indexed by the parts (form field) name.
 * 
 * @author bratseth
 */
public class MultipartParser {

    /**
     * Parses the given multi-part request and returns all the parts indexed by their name.
     * 
     * @throws IllegalArgumentException if this request is not a well-formed request with Content-Type multipart/form-data
     */
    public Map<String, byte[]> parse(HttpRequest request) {
        return parse(request.getHeader("Content-Type"), request.getData(), request.getUri());
    }

    /**
     * Parses the given data stream for the given uri using the provided content-type header to determine boundaries.
     *
     * @throws IllegalArgumentException if this is not a well-formed request with Content-Type multipart/form-data
     */
    public Map<String, byte[]> parse(String contentTypeHeader, InputStream data, URI uri) {
        try {
            ParameterParser parameterParser = new ParameterParser();
            Map<String, String> contentType = parameterParser.parse(contentTypeHeader, ';');
            if (contentType.containsKey("application/zip"))
                return Map.of(EnvironmentResource.APPLICATION_ZIP, data.readAllBytes());
            if ( ! contentType.containsKey("multipart/form-data"))
                throw new IllegalArgumentException("Expected a multipart or application/zip message, but got Content-Type: " + contentTypeHeader);
            String boundary = contentType.get("boundary");
            if (boundary == null)
                throw new IllegalArgumentException("Missing boundary property in Content-Type header");
            MultipartStream multipartStream = new MultipartStream(data, boundary.getBytes(), 1 << 20, null);
            boolean nextPart = multipartStream.skipPreamble();
            Map<String, byte[]> parts = new HashMap<>();
            while (nextPart) {
                String[] headers = multipartStream.readHeaders().split("\r\n");
                String contentDispositionContent = findContentDispositionHeader(headers);
                if (contentDispositionContent == null)
                    throw new IllegalArgumentException("Missing Content-Disposition header in a multipart body part");
                Map<String, String> contentDisposition = parameterParser.parse(contentDispositionContent, ';');
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                multipartStream.readBodyData(output);
                parts.put(contentDisposition.get("name"), output.toByteArray());
                nextPart = multipartStream.readBoundary();
            }
            return parts;
        }
        catch(MultipartStream.MalformedStreamException e) {
            throw new IllegalArgumentException("Malformed multipart/form-data request", e);
        } 
        catch(IOException e) {
            throw new IllegalArgumentException("IO error reading multipart request " + uri, e);
        }
    }
    
    private String findContentDispositionHeader(String[] headers) {
        String contentDisposition = "Content-Disposition:";
        for (String header : headers) {
            if (header.length() < contentDisposition.length()) continue;
            if ( ! header.substring(0, contentDisposition.length()).equalsIgnoreCase(contentDisposition)) continue;
            return header.substring(contentDisposition.length() + 1);
        }
        return null;
    }

}
