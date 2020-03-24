// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.runner;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/**
 * @author valerijf
 */
public class FormatInputStream {

    private InputStream inputStream;
    private Format format;

    /**
     * Creates a single data input stream from either file or InputStream depending on which one is present. Preference
     * for file if both present. Additionally also detects input data format of the result stream, throws
     * IllegalArgumentException if unable to determine data format.
     *
     * @param stream              InputStream of the data if present
     * @param inputFile           path to file to use as input
     * @param addRootElementToXml to add vespafeed root element around the input data stream
     * @throws IOException        on errors
     */
    public FormatInputStream(InputStream stream, Optional<String> inputFile, boolean addRootElementToXml)
            throws IOException {
        DataFormatDetector dataFormatDetector = new DataFormatDetector(new JsonFactory(), new XmlFactory());
        DataFormatMatcher formatMatcher;

        if (inputFile.isPresent()) {
            try (FileInputStream fileInputStream = new FileInputStream(inputFile.get())) {
                formatMatcher = dataFormatDetector.findFormat(fileInputStream);
            }
            inputStream = new FileInputStream(inputFile.get());

        } else {
            if (stream.available() == 0)
                System.out.println("No data in stream yet and no file specified, waiting for data.");

            inputStream = stream.markSupported() ? stream : new BufferedInputStream(stream);
            inputStream.mark(DataFormatDetector.DEFAULT_MAX_INPUT_LOOKAHEAD);
            formatMatcher = dataFormatDetector.findFormat(inputStream);
            inputStream.reset();
        }

        if (addRootElementToXml) {
            inputStream = addVespafeedTag(inputStream);
            format = Format.XML;
            return;
        }

        if (formatMatcher.getMatchStrength() == MatchStrength.INCONCLUSIVE
            || formatMatcher.getMatchStrength() == MatchStrength.NO_MATCH) {
            throw new IllegalArgumentException("Could not detect input format");
        }

        switch (formatMatcher.getMatchedFormatName().toLowerCase()) {
            case "json":
                format = Format.JSON;
                break;
            case "xml":
                format = Format.XML;
                break;
            default:
                throw new IllegalArgumentException("Unknown data format");
        }
    }

    private static InputStream addVespafeedTag(InputStream inputStream) {
        return new SequenceInputStream(Collections.enumeration(Arrays.asList(
                new ByteArrayInputStream("<vespafeed>".getBytes()), inputStream,
                new ByteArrayInputStream("</vespafeed>".getBytes())))
        );
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public Format getFormat() {
        return format;
    }

    public enum Format {
        JSON, XML
    }

}
