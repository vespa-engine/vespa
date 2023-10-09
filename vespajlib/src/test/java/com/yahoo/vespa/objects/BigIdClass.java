// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

import java.nio.ByteBuffer;

public class BigIdClass extends Identifiable
{
    public static final int classId = registerClass(42, BigIdClass.class);

    static public final FieldBase fByte       = new FieldBase("myByte");
    static public final FieldBase fShort      = new FieldBase("myShort");
    static public final FieldBase fInt        = new FieldBase("myInt");
    static public final FieldBase fLong       = new FieldBase("myLong");
    static public final FieldBase fFloat      = new FieldBase("myFloat");
    static public final FieldBase fDouble     = new FieldBase("myDouble");
    static public final FieldBase fArrayOne   = new FieldBase("myArrayOne");
    static public final FieldBase fArrayTwo   = new FieldBase("myArrayTwo");
    static public final FieldBase fByteBuffer = new FieldBase("myByteBuffer");
    static public final FieldBase fString     = new FieldBase("myString");
    static public final FieldBase fAlternate  = new FieldBase("myAlternate");
    static public final FieldBase fChildOne   = new FieldBase("childOne");
    static public final FieldBase fChildTwo   = new FieldBase("childTwo");

    private byte myByte = 42;
    private short myShort = 4242;
    private int myInt = 424242;
    private long myLong = 9876543210L;
    private float myFloat = 42.42f;
    private double myDouble = 42.4242e-42;

    private byte[] myArrayOne = new byte[5];
    private byte[] myArrayTwo = new byte[10];
    private ByteBuffer myByteBuffer;

    private String myString = "default-value";
    private String myAlternate = "some \u2603 Utf8";

    private Identifiable childOne = null;
    private Identifiable childTwo = new FooBarIdClass();

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("", childOne);
        visitor.visit("one", childOne);
        visitor.visit("two", childTwo);
        visitor.visit(null, childTwo);
        visitor.visit("myArrayOne", myArrayOne);
    }

    public BigIdClass() {
        myArrayOne[0] = 1;
        myArrayOne[1] = 2;
        myArrayOne[2] = 3;
        myArrayOne[3] = 4;
        myArrayOne[4] = 5;

        myArrayTwo[0] = 6;
        myArrayTwo[1] = 7;
        myArrayTwo[2] = 8;

        myArrayTwo[9] = 9;
    }

    public BigIdClass(int value) {
        myByte = (byte)value;
        myShort = (short)value;
        myInt = value;
        myLong = value;
        myLong <<= 30;
        myLong ^= value;
        myFloat = (float)(value + 0.000001*value);
        myDouble = 123456.789*value + 0.987654321*value;
        myArrayOne[1] = (byte)(value >> 1);
        myArrayOne[2] = (byte)(value >> 5);
        myArrayOne[3] = (byte)(value >> 9);

        myArrayTwo[3] = (byte)(value >> 2);
        myArrayTwo[4] = (byte)(value >> 4);
        myArrayTwo[5] = (byte)(value >> 6);
        myArrayTwo[6] = (byte)(value >> 8);

        myString = Integer.toString(value);
        myAlternate = "a \u2603 " + Integer.toString(value) + " b";

        childOne = new FooBarIdClass();
        childTwo = null;
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        buf.putByte(fByte, myByte);
        buf.putShort(fShort, myShort);
        buf.putInt(fInt, myInt);
        buf.putLong(fLong, myLong);
        buf.putFloat(fFloat, myFloat);
        buf.putDouble(fDouble, myDouble);
        buf.put(fArrayOne, myArrayOne);
        buf.put(fArrayTwo, myArrayTwo);
        /* buf.put(fByteBuffer, myByteBuffer); */
        buf.put(fString, myString);
        putUtf8(buf, myAlternate);

        serializeOptional(buf, childOne);
        serializeOptional(buf, childTwo);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {

        myByte = buf.getByte(fByte);
        myShort = buf.getShort(fShort);
        myInt = buf.getInt(fInt);
        myLong = buf.getLong(fLong);
        myFloat = buf.getFloat(fFloat);
        myDouble = buf.getDouble(fDouble);

        myArrayOne = buf.getBytes(fArrayOne, 5);
        myArrayTwo = buf.getBytes(fArrayTwo, 10);

        myString = buf.getString(fString);
        myAlternate = getUtf8(buf);

        childOne = deserializeOptional(buf);
        childTwo = deserializeOptional(buf);
    }

    public boolean equals(Object other) {
        if (super.equals(other) && other instanceof BigIdClass) {
            boolean allEq = true;
            BigIdClass o = (BigIdClass)other;
            if (myByte   != o.myByte)   { allEq = false; }
            if (myShort  != o.myShort)  { allEq = false; }
            if (myInt    != o.myInt)    { allEq = false; }
            if (myLong   != o.myLong)   { allEq = false; }
            if (myFloat  != o.myFloat)  { allEq = false; }
            if (myDouble != o.myDouble) { allEq = false; }
            if (! myString.equals(o.myString)) { allEq = false; }
            if (! equals(childOne, o.childOne)) { allEq = false; }
            if (! equals(childTwo, o.childTwo)) { allEq = false; }
            if (childTwo != null && o.childTwo == null) { allEq = false; }
            return allEq;
        }
        return false;
    }

/***
    public boolean diff(BigIdClass o) {
            boolean allEq = true;

            if (myByte != o.myByte) { System.out.println("myByte differ: "+myByte+" != "+o.myByte); allEq = false; }
            if (myShort != o.myShort) { System.out.println("myShort differ: "+myShort+" != "+o.myShort); allEq = false; }
            if (myInt != o.myInt) { System.out.println("myInt differ: "+myInt+" != "+o.myInt); allEq = false; }
            if (myLong != o.myLong) { System.out.println("myLong differ: "+myLong+" != "+o.myLong); allEq = false; }
            if (myFloat != o.myFloat) { System.out.println("myFloat differ: "+myFloat+" != "+o.myFloat); allEq = false; }
            if (myDouble != o.myDouble) { System.out.println("myDouble differ: "+myDouble+" != "+o.myDouble); allEq = false; }
            if (! myString.equals(o.myString)) { System.out.println("myString differ: "+myString+" != "+o.myString); allEq = false; }
            if (childOne == null && o.childOne != null) {
                System.err.println("childOne is null, o.childOne is: "+o.childOne);
                allEq = false;
            }
            if (childOne != null && o.childOne == null) {
                System.err.println("o.childOne is null, childOne is: "+childOne);
                allEq = false;
            }
            if (childTwo == null && o.childTwo != null) {
                System.err.println("childTwo is null, o.childTwo is: "+o.childTwo);
                allEq = false;
            }
            if (childTwo != null && o.childTwo == null) {
                System.err.println("o.childTwo is null, childTwo is: "+childTwo);
                allEq = false;
            }
            return allEq;
    }
***/

}
