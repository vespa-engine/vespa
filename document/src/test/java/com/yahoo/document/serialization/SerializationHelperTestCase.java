// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.datatypes.Raw;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.text.Utf8;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.objects.BufferSerializer;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Einar M R Rosenvinge
 */
@SuppressWarnings("deprecation")
public class SerializationHelperTestCase {

    @Test
    public void testGetNullTerminatedString() {
        //This is a test.0ab
        byte[] test = {0x54, 0x68, 0x69, 0x73, 0x20, 0x69, 0x73, 0x20, 0x61, 0x20, 0x74, 0x65, 0x73, 0x74, 0x2e, 0x0,
                0x61, 0x62};

        BufferSerializer data = BufferSerializer.wrap(test);

        assertTrue(data.position() == 0);

        Utf8Array thisIsATest = VespaDocumentDeserializer42.parseNullTerminatedString(data.getBuf().getByteBuffer());

        assertTrue(thisIsATest.equals(new Utf8Array(Utf8.toBytes("This is a test."))));
        assertTrue(data.position() == 16);
        assertTrue(test[16] == 0x61); //a

        data.position(0);

        assertTrue(data.position() == 0);

        Utf8Array thisIsATestAgain = VespaDocumentDeserializer42.parseNullTerminatedString(data.getBuf().getByteBuffer(), 15);

        assertTrue(thisIsATestAgain.equals(new Utf8Array(Utf8.toBytes("This is a test."))));
        assertTrue(data.position() == 16);
        assertTrue(test[16] == 0x61); //a
    }

    @Test
    public void testSerializeRawField() {
        GrowableByteBuffer gbuf = new GrowableByteBuffer();
        ByteBuffer rawValue = ByteBuffer.wrap(Utf8.toBytes("0123456789"));
        rawValue.position(7);
        Raw value = new Raw(rawValue);
        value.serialize(gbuf);

        assertEquals(7, gbuf.position());
        assertEquals(7, rawValue.position());

        value = new Raw(rawValue);
        value.serialize(gbuf);

        assertEquals(14, gbuf.position());
        assertEquals(7, rawValue.position());
    }

}
