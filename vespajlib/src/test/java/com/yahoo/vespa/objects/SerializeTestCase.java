// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

import com.yahoo.io.GrowableByteBuffer;
import java.nio.ByteOrder;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author arnej27959
 */
public class SerializeTestCase {

    @Test
    public void testSimple() {
        SomeIdClass s = new SomeIdClass();
        BufferSerializer buf = new BufferSerializer(new GrowableByteBuffer());
        s.serialize(buf);
        buf.flip();
        s.deserialize(buf);
    }

    @Test
    public void testOne() {
        SomeIdClass s = new SomeIdClass();
        BufferSerializer buf = new BufferSerializer(new GrowableByteBuffer());
        s.serializeWithId(buf);
        buf.flip();
        Identifiable s2 = Identifiable.create(buf);
        assertThat((s2 instanceof SomeIdClass), is(true));
    }

    @Test
    public void testUnderflow() {
        BufferSerializer buf = new BufferSerializer(new GrowableByteBuffer());
        buf.putByte(null, (byte)123);
        buf.flip();
        boolean caught = false;
        try {
            byte[] val = buf.getBytes(null, 2);
        } catch (IllegalArgumentException e) {
            // System.out.println(e);
            caught = true;
        }
        assertThat(caught, is(true));
    }

    @Test
    public void testIdNotFound() {
        BufferSerializer buf = new BufferSerializer(new GrowableByteBuffer());
        buf.putInt(null, 717273);
        buf.flip();
        boolean caught = false;
        try {
            Identifiable nsi = Identifiable.create(buf);
        } catch (IllegalArgumentException e) {
            // System.out.println(e);
            caught = true;
        }
        assertThat(caught, is(true));
    }

    @Test
    public void testOrdering() {
        BufferSerializer buf = new BufferSerializer(new GrowableByteBuffer());
        assertThat(buf.order(), is(ByteOrder.BIG_ENDIAN));
        buf.putInt(null, 0x11223344);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(null, 0x55667788);
        assertThat(buf.order(), is(ByteOrder.LITTLE_ENDIAN));
        buf.flip();
        assertThat(buf.getByte(null), is((byte)0x11));
        assertThat(buf.getByte(null), is((byte)0x22));
        assertThat(buf.getShort(null), is((short)0x4433));
        buf.order(ByteOrder.BIG_ENDIAN);
        assertThat(buf.getByte(null), is((byte)0x88));
        assertThat(buf.getByte(null), is((byte)0x77));
        assertThat(buf.getShort(null), is((short)0x6655));
    }

    @Test
    public void testBig() {
        BigIdClass dv = new BigIdClass();
        BigIdClass ov = new BigIdClass(6667666);
        BigIdClass bv = new BigIdClass(123456789);

        assertThat(BigIdClass.classId, is(42));
        assertThat(dv.getClassId(), is(42));
        assertThat(ov.getClassId(), is(42));
        assertThat(bv.getClassId(), is(42));

        assertThat(ov.equals(dv), is(false));
        assertThat(dv.equals(bv), is(false));
        assertThat(bv.equals(ov), is(false));

        BufferSerializer buf = new BufferSerializer(new GrowableByteBuffer());
        ov.serialize(buf);
        buf.flip();
        dv.deserialize(buf);
        assertThat(ov, equalTo(dv));
        assertThat(dv, equalTo(ov));
        buf = new BufferSerializer(new GrowableByteBuffer());
        bv.serializeWithId(buf);
        buf.flip();
        dv.deserializeWithId(buf);
        assertThat(bv, equalTo(dv));
        assertThat(dv, equalTo(bv));

        buf = new BufferSerializer(new GrowableByteBuffer());
        SomeIdClass s = new SomeIdClass();
        assertThat(dv.equals(s), is(false));
        assertThat(ov.equals(s), is(false));
        assertThat(bv.equals(s), is(false));
        assertThat(dv.equals(new Object()), is(false));

        s.serializeWithId(buf);
        buf.flip();
        boolean caught = false;
        try {
            dv.deserializeWithId(buf);
        } catch (IllegalArgumentException ex) {
            caught = true;
            // System.out.println(ex);
        }
        assertThat(caught, is(true));
        buf = new BufferSerializer(new GrowableByteBuffer());
        buf.putLong(null, 0x7777777777777777L);
        buf.flip();
        caught = false;
        try {
            dv.deserializeWithId(buf);
        } catch (IllegalArgumentException ex) {
            caught = true;
            // System.out.println(ex);
        }
        assertThat(caught, is(true));
    }

}
