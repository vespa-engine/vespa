package com.yahoo.tensor.serialization;

import java.io.IOException;

public interface DataDisclosure {

    void writeNull();
    void writeBool(boolean b);
    void writeLong(long value);

    void writeDouble(double value);

    void writeString(byte[] value);

    void startArray() throws IOException;
    void endArray() throws IOException;

    void startObject() throws IOException;

    void endObject() throws IOException;

    void writeFieldName(String name) throws IOException;
}
