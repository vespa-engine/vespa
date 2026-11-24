// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.process.Embedder.Image;
import com.yahoo.language.process.Embedder.Input;
import com.yahoo.language.process.Embedder.Modality;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.language.process.Embedder.Modality.IMAGE;
import static com.yahoo.language.process.Embedder.Modality.TEXT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * @author glebashnik
 */
public class EmbedderInputTestCase {

    /** The 3 byte signature of a JPEG file. */
    private static final byte[] jpeg = { (byte)0xFF, (byte)0xD8, (byte)0xFF };

    @Test
    public void testEmbedderAcceptsOnlyTextByDefault() {
        Embedder embedder = new Embedder() {

            @Override
            public List<Integer> embed(String text, Context context) { throw new UnsupportedOperationException(); }

            @Override
            public Tensor embed(String text, Context context, TensorType tensorType) {
                return Tensor.from(tensorType, "[" + text.length() + "]");
            }

        };
        assertEquals(Set.of(Set.of(TEXT)), embedder.supportedModalities());

        var context = new Embedder.Context("test");
        var tensorType = TensorType.fromSpec("tensor(x[1])");
        assertEquals(Tensor.from(tensorType, "[5]"), embedder.embed(Input.of("hello"), context, tensorType));
        var e = assertThrows(IllegalArgumentException.class,
                             () -> embedder.embed(Input.of("hello", new Image(jpeg)), context, tensorType));
        assertEquals("This embedder accepts text, but is given text and image", e.getMessage());
    }

    @Test
    public void testFailingEmbedderAcceptsAllInput() {
        assertEquals(Set.of(Set.of(TEXT), Set.of(IMAGE), Set.of(TEXT, IMAGE)),
                     Embedder.throwsOnUse.supportedModalities());
        // A new modality must also be added to FailingEmbedder: n modalities have 2^n - 1 combinations
        assertEquals((1 << Modality.values().length) - 1, Embedder.throwsOnUse.supportedModalities().size());
    }

    @Test
    public void testInputHasTextOrImageOrBoth() {
        assertEquals(Set.of(TEXT), Input.of("hello").modalities());
        assertEquals(Set.of(IMAGE), Input.of(new Image(jpeg)).modalities());
        assertEquals(Set.of(TEXT, IMAGE), Input.of("hello", new Image(jpeg)).modalities());
        var e = assertThrows(IllegalArgumentException.class, () -> new Input(Optional.empty(), Optional.empty()));
        assertEquals("Embedder input must have a text or an image", e.getMessage());
    }

    @Test
    public void testEmbedderRejectsModalitiesItDoesNotAccept() {
        Set<Set<Modality>> text = Set.of(Set.of(TEXT));
        assertEquals(Optional.empty(), Embedder.rejectionReason(Set.of(TEXT), text));
        assertEquals(Optional.of("accepts text, but is given image"), Embedder.rejectionReason(Set.of(IMAGE), text));
        assertEquals(Optional.of("accepts text, but is given text and image"),
                     Embedder.rejectionReason(Set.of(TEXT, IMAGE), text));

        Set<Set<Modality>> textOrImage = Set.of(Set.of(TEXT), Set.of(IMAGE));
        assertEquals(Optional.empty(), Embedder.rejectionReason(Set.of(TEXT), textOrImage));
        assertEquals(Optional.empty(), Embedder.rejectionReason(Set.of(IMAGE), textOrImage));
        assertEquals(Optional.of("accepts image or text, but is given text and image"),
                     Embedder.rejectionReason(Set.of(TEXT, IMAGE), textOrImage));

        Set<Set<Modality>> imageWithOptionalText = Set.of(Set.of(IMAGE), Set.of(TEXT, IMAGE));
        assertEquals(Optional.empty(), Embedder.rejectionReason(Set.of(TEXT, IMAGE), imageWithOptionalText));
        assertEquals(Optional.of("accepts image or text and image, but is given text"),
                     Embedder.rejectionReason(Set.of(TEXT), imageWithOptionalText));
    }

    @Test
    public void testEmbedderAcceptsModalitiesOfInput() {
        Set<Set<Modality>> all = Set.of(Set.of(TEXT), Set.of(IMAGE), Set.of(TEXT, IMAGE));
        assertEquals(Optional.empty(), Embedder.rejectionReason(Input.of("hello").modalities(), all));
        assertEquals(Optional.empty(), Embedder.rejectionReason(Input.of(new Image(jpeg)).modalities(), all));
        assertEquals(Optional.empty(), Embedder.rejectionReason(Input.of("hello", new Image(jpeg)).modalities(), all));
    }

    @Test
    public void testImageHasMediaTypeOfItsFormat() {
        assertEquals("image/jpeg", new Image(new byte[] { (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0 }).mediaType());
        assertEquals("image/png", new Image(new byte[] { (byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }).mediaType());
        assertEquals("image/gif", new Image("GIF89a".getBytes()).mediaType());
    }

    @Test
    public void testImageMustHaveRecognizedFormat() {
        var e = assertThrows(IllegalArgumentException.class, () -> new Image(new byte[] { 1, 2, 3 }));
        assertEquals("Binary data to embed must be a JPEG, PNG or GIF image", e.getMessage());
        // A WebP image is not recognized
        assertThrows(IllegalArgumentException.class, () -> new Image("RIFF\0\0\0\0WEBPVP8 ".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> new Image(new byte[0]));
    }

}
