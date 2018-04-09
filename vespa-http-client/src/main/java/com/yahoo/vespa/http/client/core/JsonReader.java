// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.yahoo.vespa.http.client.FeedClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads a stream of json documents and sends them to feedClient.
 *
 * @author dybis
 */
public class JsonReader {

    /**
     * Max size of documents. As we stream docs in for finding doc id, we buffer the data and later stream them to
     * feedclient after doc id has been revealed.
     */
    private final static int maxDocumentSizeChars = 50 * 1024 * 1024;

    // Intended to be used as static.
    private JsonReader() {}

    /**
     * Process one inputstream and send all documents to feedclient.
     * @param inputStream source of array of json document.
     * @param feedClient where data is sent.
     * @param numSent counter to be incremented for every document streamed.
     */
    public static void read(InputStream inputStream, FeedClient feedClient, AtomicInteger numSent) {
        try {
            final InputStreamJsonElementBuffer jsonElementBuffer = new InputStreamJsonElementBuffer(inputStream);
            final JsonFactory jfactory = new JsonFactory().disable(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES);
            final JsonParser jParser = jfactory.createParser(jsonElementBuffer);
            while (true) {
                String docId = parseOneDocument(jParser);
                if (docId == null) {
                    break;
                }
                CharSequence data = jsonElementBuffer.getJsonAsArray(jParser.getCurrentLocation().getCharOffset());
                if (data == null) {
                    continue;
                }
                feedClient.stream(docId, data);
                numSent.incrementAndGet();
            }
            jsonElementBuffer.close();
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
            throw new RuntimeException(ioe);
        }
    }

    /**
     * This class is intended to be used with a json parser. The data is sent through this intermediate stream
     * and to the parser. When the parser is done with a document, it calls postJsonAsArray which will
     * stream the document up to the current position of the parser.
     */
    private static class InputStreamJsonElementBuffer extends InputStreamReader {

        /**
         * Simple class implementing a circular array with some custom function used for finding start and end
         * of json object. The reason this is needed is that the json parser reads more than it parses
         * from the input stream (seems like about 8k). Using a ByteBuffer and manually moving data
         * is an order of magnitude slower than this implementation.
         */
        private class CircularCharBuffer {

            int readPointer = 0;
            int writePointer = 0;
            final char[] data;
            final int size;

            public CircularCharBuffer(int chars) {
                data = new char[chars];
                size = chars;
            }

            /**
             * This is for throwing away [ and spaces in front of a json object, and find the position of {.
             * Not for parsing much text.
             * @return posisiton for {
             */
            public int findNextObjectStart() {
                int readerPos = 0;
                while (get(readerPos) != '{') {
                    readerPos++;
                    assert(readerPos<=size);
                }
                return readerPos;
            }

            /**
             * This is for throwing away comma and or ], and for finding the position of the last }.
             * @param fromPos where to start searching
             * @return position for }
             */
            public int findLastObjectEnd(int fromPos) {
                while (get(fromPos-1) != '}') {
                    fromPos--;
                    assert(fromPos >=0);
                }
                return fromPos;
            }

            public void put(char dataByte) {
                data[writePointer] = dataByte;
                writePointer++;
                if (writePointer >= size) writePointer = 0;
                assert(writePointer != readPointer);
            }

            public char get(int pos) {
                int readPos = readPointer + pos;
                if (readPos >= size) readPos -= size;
                assert(readPos != writePointer);
                return data[readPos];
            }

            public void advance(int end) {
                readPointer += end;
                if (readPointer >= size) readPointer -= size;
            }
        }

        private final CircularCharBuffer circular = new CircularCharBuffer(maxDocumentSizeChars);
        private int processedChars = 0;

        public InputStreamJsonElementBuffer(InputStream inputStream) {
            super(inputStream, StandardCharsets.UTF_8);
        }

        /**
         * Removes comma, start/end array tag (last element), spaces etc that might be surrounding a json element.
         * Then sends the element to the outputstream.
         * @param parserPosition how far the parser has come. Please note that the parser might have processed
         *                       more data from the input source as it is reading chunks of data.
         * @throws IOException on errors
         */
        public CharSequence getJsonAsArray(long parserPosition) throws IOException {
            final int charSize = (int)parserPosition - processedChars;
            if (charSize<2) {
                return null;
            }
            final int endPosOfJson = circular.findLastObjectEnd(charSize);
            final int startPosOfJson = circular.findNextObjectStart();
            processedChars += charSize;
            // This can be optimized since we rarely wrap the circular buffer.
            StringBuilder dataBuffer = new StringBuilder(endPosOfJson - startPosOfJson);
            for (int x = startPosOfJson; x < endPosOfJson; x++) {
                dataBuffer.append(circular.get(x));
            }
            circular.advance(charSize);
            return dataBuffer.toString();
        }

        @Override
        public int read(char[] b, int off, int len) throws IOException {
            int length = 0;
            int value = 0;
            while (length < len && value != -1) {
                value = read();
                if (value == -1) {
                    return length == 0 ? -1 : length;
                }
                b[off + length] = (char) value;
                length++;
            }
            return length;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) circular.put((char)value);
            return value;
        }
    }

    /**
     * Parse one document from the stream and return doc id.
     * @param jParser parser with stream.
     * @return doc id of document or null if no more docs.
     * @throws IOException on problems
     */
    private static String parseOneDocument(JsonParser jParser) throws IOException {
        int objectLevel = 0;
        String documentId = null;
        boolean valueIsDocumentId = false;
        while (jParser.nextToken() != null) {
            final String tokenAsText = jParser.getText();
            if (valueIsDocumentId) {
                if (documentId != null) {
                    throw new RuntimeException("Several document ids");
                }
                documentId = tokenAsText;
                valueIsDocumentId = false;
            }
            switch(jParser.getCurrentToken()) {
                case START_OBJECT:
                    objectLevel++;
                    break;
                case END_OBJECT:
                    objectLevel--;
                    if (objectLevel == 0) {
                        return documentId;
                    }
                    break;
                case FIELD_NAME:
                    if (objectLevel == 1 &&
                            (tokenAsText.equals("put")
                                    || tokenAsText.endsWith("id")
                                    || tokenAsText.endsWith("update")
                                    || tokenAsText.equals("remove"))) {
                        valueIsDocumentId = true;
                    }
                    break;
                default: // No operation on all other tags.
            }
        }
        return null;
    }
}
