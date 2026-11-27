// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.language.process.Embedder;
import com.yahoo.search.Query;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.RankProfile.InputType;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.language.process.Embedder.Modality.IMAGE;
import static com.yahoo.language.process.Embedder.Modality.TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests query embed expressions with an image, and with a text and an image: embed(embedder, @text, @image).
 *
 * @author glebashnik
 */
class QueryMultimodalEmbeddingTest {

    /** The 3 byte signature of a JPEG file, as a base64 data URL. */
    private static final String image = dataUrl(new byte[] { (byte)0xFF, (byte)0xD8, (byte)0xFF });

    private final Map<String, Embedder> embedders = Map.of(
            "textAndImage", new InputEmbedder("textAndImage", Set.of(Set.of(TEXT), Set.of(IMAGE), Set.of(TEXT, IMAGE))),
            "textOrImage", new InputEmbedder("textOrImage", Set.of(Set.of(TEXT), Set.of(IMAGE))),
            "imageOnly", new InputEmbedder("imageOnly", Set.of(Set.of(IMAGE))),
            "textOnly", new TextOnlyEmbedder());

    /** A single embedder, which is required for embed() with no embedder id. */
    private final Map<String, Embedder> single = Map.of("textAndImage", embedders.get("textAndImage"));

    @Test
    void testTextAndImage() {
        assertEmbedding("[5, 103, 0]", "embed(textAndImage, @text, @image)", Map.of("text", "hello", "image", image));
        // The order of the references has no meaning
        assertEmbedding("[5, 103, 0]", "embed(textAndImage, @image, @text)", Map.of("text", "hello", "image", image));
        assertEmbedding("[5, 103, 0]", "embed(@text, @image)", Map.of("text", "hello", "image", image), single);
    }

    @Test
    void testSingleArgument() {
        assertEmbedding("[5, 0, 0]", "embed(textAndImage, @text)", Map.of("text", "hello"));
        assertEmbedding("[5, 0, 0]", "embed(textAndImage, 'hello')", Map.of());
        assertEmbedding("[0, 103, 0]", "embed(textAndImage, @image)", Map.of("image", image));
        assertEmbedding("[0, 103, 0]", "embed(textAndImage, '" + image + "')", Map.of());
        assertEmbedding("[0, 103, 0]", "embed(" + image + ")", Map.of(), single);
        assertEmbedding("[0, 103, 0]", "embed(@image)", Map.of("image", image), single);
    }

    @Test
    void testTextIsNotSplitAtComma() {
        assertEmbedding("[12, 0, 0]", "embed(textAndImage, 'hello, world')", Map.of());
        assertEmbedding("[12, 0, 0]", "embed(textAndImage, @text)", Map.of("text", "hello, world"));
        assertEmbedding("[12, 0, 0]", "embed(hello, world)", Map.of(), single);
        // A reference after the comma does not split the text
        assertEmbedding("[17, 0, 0]", "embed(red shoes, @image)", Map.of("image", image), single);
    }

    @Test
    void testBlankArgumentsAreLeftOut() {
        assertEmbedding("[0, 103, 0]", "embed(textAndImage, @text, @image)", Map.of("text", " ", "image", image));
        assertEmbedding("[5, 0, 0]", "embed(textAndImage, @text, @image)", Map.of("text", "hello", "image", ""));
        assertEmbedding("[0, 0, 0]", "embed(textAndImage, @text, @image)", Map.of("text", "", "image", ""));
        assertEmbedding("[0, 0, 0]", "embed(textAndImage, @text)", Map.of("text", " "));
    }

    @Test
    void testQueryValues() {
        String jpeg = "data:image/jpeg;base64,/9j/";
        List<ValueCase> cases = List.of(
                // Images
                ValueCase.image(jpeg, 3),
                ValueCase.image("data:image/png;base64,iVBORw0KGgo=", 8),
                ValueCase.image("data:image/gif;base64,R0lGODlh", 6),
                ValueCase.image("DATA:IMAGE/JPEG;BASE64,/9j/", 3),
                ValueCase.image("data:image/jpeg;name=photo.jpg;base64,/9j/", 3),
                ValueCase.image("data:image/png;base64,/9j/", 3), // The format comes from the bytes
                ValueCase.image("data:image/;base64,/9j/", 3),
                ValueCase.image("  " + jpeg, 3),
                ValueCase.image(jpeg + "\n", 3), // A newline at the end of a file
                ValueCase.image(" " + jpeg + "\n", 3),
                ValueCase.image(jpeg + "\nAAEC", 6), // Wrapped lines, as from GNU base64
                ValueCase.image(jpeg + "\r\nAAEC", 6),
                ValueCase.image(jpeg + "    ", 6), // Each space reads as a '+': /9j/++++
                ValueCase.image("data:image/jpeg;base64,/9j/AAE", 5), // Missing padding
                ValueCase.image("data:image/jpeg;base64,/9j/AA==", 4),
                // Errors for an embedder which accepts images
                ValueCase.error("data:image/png,%89PNG", noBase64),
                ValueCase.error("data:image/jpeg;base64,not base64!", invalidBase64),
                ValueCase.error("data:image/jpeg;base64,AQID", notImage),
                ValueCase.error("data:image/webp;base64,UklGRgAAAABXRUJQ", notImage),
                ValueCase.error("data:image/jpeg;base64,", notImage),
                ValueCase.error(jpeg + "\t", invalidBase64),
                ValueCase.error("data:image/png;base64,iVBORw0KGgo= ", invalidBase64), // The space reads as a '+', which cannot follow '='
                ValueCase.error("data:image/jpeg;base64,_9j_", invalidBase64), // URL-safe base64
                ValueCase.error("/9j/", noPrefix),
                ValueCase.error("iVBORw0KGgo=", noPrefix),
                ValueCase.error("/9j/\nAAEC", noPrefix),
                ValueCase.error("data:image/png;base64, is the prefix", notImage), // Reads as a data URL
                // Texts
                ValueCase.text("hello"),
                ValueCase.text("data:image/png files are lossless"),
                ValueCase.text("a data:image/png;base64,/9j/ value"),
                ValueCase.text("data:image"),
                ValueCase.text("data:image/png;base64"),
                ValueCase.text("data: science"),
                ValueCase.text("data:text/plain;base64,SGVsbG8="),
                ValueCase.text("/9j/ starts a JPEG"),
                ValueCase.text("R0lGODlh is a GIF"),
                ValueCase.text("SGVsbG8="), // Base64 of data which is not an image
                // Blank values
                ValueCase.blank(""),
                ValueCase.blank("   "),
                ValueCase.blank("\n"));

        List<String> mismatches = new ArrayList<>();
        for (ValueCase c : cases) {
            for (String embedderId : List.of("textAndImage", "textOrImage", "imageOnly", "textOnly")) {
                String expected = c.expectedBy(embedderId);
                String actual = outcome("embed(" + embedderId + ", @value)", Map.of("value", c.value()));
                if ( ! matches(expected, actual))
                    mismatches.add(embedderId + " with '" + escaped(c.value()) + "': expected " + expected +
                                   ", but got " + actual);
            }
        }
        assertEquals(List.of(), mismatches);
    }

    @Test
    void testTwoReferences() {
        String jpeg = "data:image/jpeg;base64,/9j/";
        String lookAlike = "data:image/png files are lossless";
        record TwoValues(String text, String image, String expected) { }
        List<TwoValues> cases = List.of(
                new TwoValues("hello", jpeg, "[5, 103, 0]"),
                new TwoValues(jpeg, "hello", "[5, 103, 0]"), // The order has no meaning
                new TwoValues(lookAlike, jpeg, "[" + lookAlike.length() + ", 103, 0]"),
                new TwoValues(" ", jpeg, "[0, 103, 0]"),
                new TwoValues("hello", "", "[5, 0, 0]"),
                new TwoValues("", "", "[0, 0, 0]"),
                new TwoValues("hello", "world", "error: embed() accepts one text, but got two"),
                new TwoValues("hello", "SGVsbG8=", "error: embed() accepts one text, but got two"),
                new TwoValues(jpeg, jpeg, "error: embed() accepts one image, but got two"),
                new TwoValues("hello", "/9j/", "error: " + noPrefix),
                new TwoValues("hello", "data:image/jpeg;base64,not base64!", "error: " + invalidBase64));

        List<String> mismatches = new ArrayList<>();
        for (TwoValues c : cases) {
            String actual = outcome("embed(textAndImage, @text, @image)", Map.of("text", c.text(), "image", c.image()));
            if ( ! matches(c.expected(), actual))
                mismatches.add("'" + escaped(c.text()) + "' and '" + escaped(c.image()) + "': expected " +
                               c.expected() + ", but got " + actual);
        }
        assertEquals(List.of(), mismatches);
    }

    @Test
    void testDataUrlWithPlusInUrl() {
        byte[] plusBytes = { (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xFB, (byte)0xEF, (byte)0xBE };
        String plusBase64 = Base64.getEncoder().encodeToString(plusBytes);
        assertEquals("/9j/++++", plusBase64);
        String embed = "&ranking.features.query(q)=" + urlEncode("embed(textAndImage, @image)");

        // URL encoded, as an HTTP client does
        String encoded = "?ranking=profile" + embed + "&image=" + urlEncode("data:image/jpeg;base64," + plusBase64);
        assertEquals(Tensor.from("tensor<float>(x[3]):[0, 106, 0]"), queryTensor(encoded, embedders));

        // Not URL encoded, as in a URL written by hand: Each '+' arrives as a space
        String notEncoded = "?ranking=profile" + embed + "&image=data:image/jpeg;base64," + plusBase64;
        assertEquals(Tensor.from("tensor<float>(x[3]):[0, 106, 0]"), queryTensor(notEncoded, embedders));
    }

    @Test
    void testInvalidArguments() {
        assertEmbeddingFails("Could not resolve query parameter reference 'image' used in an embed() argument",
                             "embed(textAndImage, @text, @image)", Map.of("text", "hello"));
        assertEmbeddingFails("An argument of embed() is a data URL whose data is not valid base64",
                             "embed(textAndImage, @text, @image)", Map.of("text", "hello", "image", "data:image/jpeg;base64,not base64!"));
        assertEmbeddingFails("Binary data to embed must be a JPEG, PNG or GIF image",
                             "embed(textAndImage, @text, @image)", Map.of("text", "hello", "image", dataUrl(new byte[] { 1, 2, 3 })));
        assertEmbeddingFails("Could not resolve query parameter reference 'text, @image, @other' used in an embed() argument",
                             "embed(textAndImage, @text, @image, @other)", Map.of("text", "hello", "image", image, "other", "hi"));
        assertEmbeddingFails("Multiple embedders are provided but the string to embed is not quoted. " +
                             "Usage: embed(embedder-id, 'text'). Available embedder ids are 'imageOnly', 'textAndImage', 'textOnly', 'textOrImage'.",
                             "embed(@text, @image)", Map.of("text", "hello", "image", image));
    }

    @Test
    void testQuotedTextNextToReferenceIsRejected() {
        String message = "embed() takes a text with an image as two references, as in " +
                         "embed(embedder-id, @text, @image), but got a quoted text next to a reference";
        assertEmbeddingFails(message, "embed('hello', @image)", Map.of("image", image), single);
        assertEmbeddingFails(message, "embed(textAndImage, 'hello', @image)", Map.of("image", image), single);
        assertEmbeddingFails(message, "embed(@image, 'hello')", Map.of("image", image), single);
        assertEmbeddingFails(message, "embed(textAndImage, @image, 'hello')", Map.of("image", image));

        // A quoted text is one text, whatever it holds
        String text = "say 'hello', @image";
        assertEmbedding("[" + text.length() + ", 0, 0]", "embed(textAndImage, \"" + text + "\")", Map.of("image", image));
    }

    @Test
    void testEmbedderWhichAcceptsTextOrImageButNotBoth() {
        assertEmbedding("[5, 0, 0]", "embed(textOrImage, @text)", Map.of("text", "hello"));
        assertEmbedding("[0, 103, 0]", "embed(textOrImage, @image)", Map.of("image", image));
        assertEmbeddingFails("Could not resolve query parameter reference 'text, @image' used in an embed() argument",
                             "embed(textOrImage, @text, @image)", Map.of("text", "hello", "image", image));
    }

    @Test
    void testEmbedderWhichOnlyAcceptsImage() {
        assertEmbedding("[0, 103, 0]", "embed(imageOnly, @image)", Map.of("image", image));
        assertEmbeddingFails("Embedder 'imageOnly' accepts image, but is given text",
                             "embed(imageOnly, @text)", Map.of("text", "hello"));
    }

    @Test
    void testTextOnlyEmbedderKeepsItsArgumentAsOneText() {
        // Only the outer quotes are removed, so the text is: a', 'bcde
        assertEmbedding("[9, 0, 0]", "embed(textOnly, 'a', 'bcde')", Map.of());
        assertEmbeddingFails("Could not resolve query parameter reference 'text, @image' used in an embed() argument",
                             "embed(textOnly, @text, @image)", Map.of("text", "hello", "image", image));
        Map<String, Embedder> singleTextOnly = Map.of("textOnly", new TextOnlyEmbedder());
        assertEmbedding("[12, 0, 0]", "embed(hello, world)", Map.of(), singleTextOnly);
        assertEmbedding("[20, 0, 0]", "embed(thanks a lot, @alice)", Map.of(), singleTextOnly);
        assertEmbedding("[15, 0, 0]", "embed('hello', @image)", Map.of("image", image), singleTextOnly);
        // A data URL and base64 of an image have no meaning to an embedder which only accepts text
        assertEmbedding("[" + image.length() + ", 0, 0]", "embed(textOnly, @image)", Map.of("image", image));
        assertEmbedding("[4, 0, 0]", "embed(textOnly, @image)", Map.of("image", "/9j/"));
    }

    private static final String invalidBase64 = "An argument of embed() is a data URL whose data is not valid base64";
    private static final String notImage = "Binary data to embed must be a JPEG, PNG or GIF image";
    private static final String noBase64 = "An argument of embed() is a data URL of an image without ';base64', " +
                                           "but only base64 data URLs are supported";
    private static final String noPrefix = "An argument of embed() is a base64 encoded image " +
                                           "without the prefix 'data:image/[format];base64,'";

    private enum Kind { TEXT, IMAGE, ERROR, BLANK }

    /** A query value, its kind, and the number of image bytes or the error message for the kinds which have one. */
    private record ValueCase(String value, Kind kind, int imageBytes, String message) {

        static ValueCase text(String value) { return new ValueCase(value, Kind.TEXT, 0, null); }
        static ValueCase image(String value, int bytes) { return new ValueCase(value, Kind.IMAGE, bytes, null); }
        static ValueCase error(String value, String message) { return new ValueCase(value, Kind.ERROR, 0, message); }
        static ValueCase blank(String value) { return new ValueCase(value, Kind.BLANK, 0, null); }

        /**
         * Returns the expected outcome for the given embedder: The cells of the embedding, or "error: " and a message.
         * An embedder which only accepts text gets every value as a text.
         */
        String expectedBy(String embedderId) {
            boolean acceptsImage = ! embedderId.equals("textOnly");
            String asText = "[" + value.length() + ", 0, 0]";
            return switch (kind) {
                case BLANK -> "[0, 0, 0]";
                case TEXT -> embedderId.equals("imageOnly")
                             ? "error: Embedder 'imageOnly' accepts image, but is given text"
                             : asText;
                case IMAGE -> acceptsImage ? "[0, " + (100 + imageBytes) + ", 0]" : asText;
                case ERROR -> acceptsImage ? "error: " + message : asText;
            };
        }

    }

    /** Returns the embedding as "[a, b, c]", or "error: " and the messages of the exception and its causes. */
    private String outcome(String embed, Map<String, String> parameters) {
        try {
            Tensor tensor = query(embed, parameters, embedders).getRanking().getFeatures().getTensor("query(q)").get();
            List<String> cells = new ArrayList<>();
            tensor.valueIterator().forEachRemaining(value -> cells.add(String.valueOf(value.intValue())));
            return "[" + String.join(", ", cells) + "]";
        } catch (Throwable t) {
            List<String> messages = new ArrayList<>();
            for (Throwable cause = t; cause != null; cause = cause.getCause())
                messages.add(cause.getMessage());
            return "error: " + String.join(" <- ", messages);
        }
    }

    /** An expected error matches any exception in the chain. */
    private static boolean matches(String expected, String actual) {
        if (expected.startsWith("error: "))
            return actual.startsWith("error: ") && actual.contains(expected.substring("error: ".length()));
        return expected.equals(actual);
    }

    private static String escaped(String value) {
        return value.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String dataUrl(byte[] image) {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image);
    }

    private void assertEmbedding(String expected, String embed, Map<String, String> parameters) {
        assertEmbedding(expected, embed, parameters, embedders);
    }

    private static void assertEmbedding(String expected, String embed, Map<String, String> parameters,
                                        Map<String, Embedder> embedders) {
        assertEquals(Tensor.from("tensor<float>(x[3]):" + expected),
                     query(embed, parameters, embedders).getRanking().getFeatures().getTensor("query(q)").get());
    }

    private void assertEmbeddingFails(String expectedMessage, String embed, Map<String, String> parameters) {
        assertEmbeddingFails(expectedMessage, embed, parameters, embedders);
    }

    private static void assertEmbeddingFails(String expectedMessage, String embed, Map<String, String> parameters,
                                             Map<String, Embedder> embedders) {
        Throwable t = assertThrows(IllegalArgumentException.class, () -> query(embed, parameters, embedders));
        for (; t != null; t = t.getCause())
            if (expectedMessage.equals(t.getMessage())) return;
        fail("Exception with message '" + expectedMessage + "' not thrown");
    }

    private static Query query(String embed, Map<String, String> parameters, Map<String, Embedder> embedders) {
        var all = new LinkedHashMap<String, String>();
        all.put("ranking.features.query(q)", embed);
        all.put("ranking", "profile");
        all.putAll(parameters);
        String request = all.entrySet().stream()
                            .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                            .collect(Collectors.joining("&", "?", ""));
        return query(request, embedders);
    }

    /** The request is the query string of a GET URL, as it is sent. */
    private static Query query(String request, Map<String, Embedder> embedders) {
        Query query = new Query.Builder().setRequest(HttpRequest.createTestRequest(request, com.yahoo.jdisc.http.HttpRequest.Method.GET))
                                         .setSchemaInfo(schemaInfo())
                                         .setEmbedders(embedders)
                                         .build();
        assertEquals(0, query.errors().size());
        return query;
    }

    private static Tensor queryTensor(String request, Map<String, Embedder> embedders) {
        return query(request, embedders).getRanking().getFeatures().getTensor("query(q)").get();
    }

    private static String urlEncode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static SchemaInfo schemaInfo() {
        var schema = new Schema.Builder("a")
                             .add(new RankProfile.Builder("profile")
                                          .addInput("query(q)", InputType.fromSpec("tensor<float>(x[3])"))
                                          .build())
                             .build();
        return new SchemaInfo(List.of(schema), List.of(new Cluster.Builder("a").addSchema("a").build()));
    }

    /**
     * An embedder which returns the length of the text in the first cell,
     * and 100 plus the number of bytes of the image in the second cell.
     * A part which the input does not have gives 0.
     */
    private static class InputEmbedder implements Embedder {

        private final String id;
        private final Set<Set<Modality>> supportedModalities;

        InputEmbedder(String id, Set<Set<Modality>> supportedModalities) {
            this.id = id;
            this.supportedModalities = supportedModalities;
        }

        @Override
        public Set<Set<Modality>> supportedModalities() { return supportedModalities; }

        @Override
        public List<Integer> embed(String text, Context context) { throw new UnsupportedOperationException(); }

        @Override
        public Tensor embed(String text, Context context, TensorType tensorType) {
            return embed(Input.of(text), context, tensorType);
        }

        @Override
        public Tensor embed(Input input, Context context, TensorType tensorType) {
            assertEquals("query(q)", context.getDestination());
            assertEquals(id, context.getEmbedderId());
            int text = input.text().map(String::length).orElse(0);
            int image = input.image().map(i -> 100 + i.data().remaining()).orElse(0);
            return Tensor.from(tensorType, "[" + text + ", " + image + ", 0]");
        }

    }

    /** An embedder which returns the length of the text in the first cell. */
    private static class TextOnlyEmbedder implements Embedder {

        @Override
        public List<Integer> embed(String text, Context context) { throw new UnsupportedOperationException(); }

        @Override
        public Tensor embed(String text, Context context, TensorType tensorType) {
            var builder = Tensor.Builder.of(tensorType);
            builder.cell(text.length(), 0);
            return builder.build();
        }

    }

}
