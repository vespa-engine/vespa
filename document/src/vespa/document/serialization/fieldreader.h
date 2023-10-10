// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib { class FieldBase; }

namespace document {
class Document;
class ArrayFieldValue;
class MapFieldValue;
class ByteFieldValue;
class IntFieldValue;
class LongFieldValue;
class FloatFieldValue;
class DoubleFieldValue;
class RawFieldValue;
class ShortFieldValue;
class StringFieldValue;
class StructFieldValue;
class WeightedSetFieldValue;

class FieldReader {
public:
    virtual ~FieldReader() {}
    virtual void read(const vespalib::FieldBase &field,
                      Document &value) = 0;
    virtual void read(const vespalib::FieldBase &field,
                      ArrayFieldValue &value) = 0;
    virtual void read(const vespalib::FieldBase &field,
                      MapFieldValue &value) = 0;
    virtual void read(const vespalib::FieldBase &field,
                      ByteFieldValue &value) = 0;
    virtual void read(const vespalib::FieldBase &field,
                      DoubleFieldValue &value) = 0;
    virtual void read(const vespalib::FieldBase &field,
                      FloatFieldValue &value) = 0;
    virtual void read(const vespalib::FieldBase &field,
                      IntFieldValue &value) = 0;
    virtual void read(const vespalib::FieldBase &field,
                      LongFieldValue &value) = 0;
    virtual void read(const vespalib::FieldBase &field,
                      RawFieldValue &value) = 0;
    virtual void read(const vespalib::FieldBase &field,
                      ShortFieldValue &value) = 0;
    virtual void read(const vespalib::FieldBase &field,
                      StringFieldValue &value) = 0;
    virtual void read(const vespalib::FieldBase &field,
                      StructFieldValue &value) = 0;
    virtual void read(const vespalib::FieldBase &field,
                      WeightedSetFieldValue &value) = 0;
};
}  // namespace document

