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
class StringFieldValue;
class ShortFieldValue;
class StructFieldValue;
class WeightedSetFieldValue;

class FieldWriter {
public:
    virtual ~FieldWriter() {}
    virtual void write(const vespalib::FieldBase &field,
                       const Document &value) = 0;
    virtual void write(const vespalib::FieldBase &field,
                       const ArrayFieldValue &value) = 0;
    virtual void write(const vespalib::FieldBase &field,
                       const MapFieldValue &value) = 0;
    virtual void write(const vespalib::FieldBase &field,
                       const ByteFieldValue &value) = 0;
    virtual void write(const vespalib::FieldBase &field,
                       const DoubleFieldValue &value) = 0;
    virtual void write(const vespalib::FieldBase &field,
                       const FloatFieldValue &value) = 0;
    virtual void write(const vespalib::FieldBase &field,
                       const IntFieldValue &value) = 0;
    virtual void write(const vespalib::FieldBase &field,
                       const LongFieldValue &value) = 0;
    virtual void write(const vespalib::FieldBase &field,
                       const RawFieldValue &value) = 0;
    virtual void write(const vespalib::FieldBase &field,
                       const ShortFieldValue &value) = 0;
    virtual void write(const vespalib::FieldBase &field,
                       const StringFieldValue &value) = 0;
    virtual void write(const vespalib::FieldBase &field,
                       const StructFieldValue &value) = 0;
    virtual void write(const vespalib::FieldBase &field,
                       const WeightedSetFieldValue &value) = 0;
};

}
