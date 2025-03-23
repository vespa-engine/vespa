package com.yahoo.tensor.serialization;

import java.io.IOException;

public interface DataDisclosure {

    void writeNull() throws IOException;
    void writeBool(boolean b) throws IOException;
    void writeLong(long value) throws IOException;

    void writeDouble(double value) throws IOException;

    void writeString(byte[] value) throws IOException;

    void startArray() throws IOException;
    void endArray() throws IOException;

    void startObject() throws IOException;

    void endObject() throws IOException;

    void writeFieldName(String name) throws IOException;
}
