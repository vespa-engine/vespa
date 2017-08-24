// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.container.jdisc.HttpRequest;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.fileupload.ParameterParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        try {
            ParameterParser parameterParser = new ParameterParser();
            Map<String, String> contentType = parameterParser.parse(request.getHeader("Content-Type"), ';');
            if ( ! contentType.containsKey("multipart/form-data"))
                throw new IllegalArgumentException("Expected a multipart message, but got Content-Type: " + 
                                                   request.getHeader("Content-Type"));
            String boundary = contentType.get("boundary");
            if (boundary == null)
                throw new IllegalArgumentException("Missing boundary property in Content-Type header");
            MultipartStream multipartStream = new MultipartStream(request.getData(), boundary.getBytes(), 
                                                                  1000 * 1000, 
                                                                  null);
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
            throw new IllegalArgumentException("IO error reading multipart request " + request.getUri(), e);
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
