// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isharedblob.h"

#include <cstring>

namespace vespalib {
class Stash;
class DataBuffer;
} // namespace vespalib
namespace vespalib::alloc {
class Alloc;
}
namespace fnet {
char* copyString(char* dst, const char* src, size_t len);
char* copyData(char* dst, const void* src, size_t len);
struct BlobRef;
} // namespace fnet
class FNET_DataBuffer;

template <typename T> struct FRT_Array {
    uint32_t _len;
    T*       _pt;
};

#define FRT_LPT(type) FRT_Array<type>

enum {
    FRT_VALUE_NONE = '\0',
    FRT_VALUE_INT8 = 'b',
    FRT_VALUE_INT8_ARRAY = 'B',
    FRT_VALUE_INT16 = 'h',
    FRT_VALUE_INT16_ARRAY = 'H',
    FRT_VALUE_INT32 = 'i',
    FRT_VALUE_INT32_ARRAY = 'I',
    FRT_VALUE_INT64 = 'l',
    FRT_VALUE_INT64_ARRAY = 'L',
    FRT_VALUE_FLOAT = 'f',
    FRT_VALUE_FLOAT_ARRAY = 'F',
    FRT_VALUE_DOUBLE = 'd',
    FRT_VALUE_DOUBLE_ARRAY = 'D',
    FRT_VALUE_STRING = 's',
    FRT_VALUE_STRING_ARRAY = 'S',
    FRT_VALUE_DATA = 'x',
    FRT_VALUE_DATA_ARRAY = 'X'
};

struct FRT_StringValue {
    uint32_t _len;
    char*    _str;
};

struct FRT_DataValue {
    uint32_t _len;
    char*    _buf;
};

union FRT_Value {
    uint8_t _intval8;
    FRT_LPT(uint8_t) _int8_array;
    uint16_t _intval16;
    FRT_LPT(uint16_t) _int16_array;
    uint32_t _intval32;
    FRT_LPT(uint32_t) _int32_array;
    uint64_t _intval64;
    FRT_LPT(uint64_t) _int64_array;
    float _float;
    FRT_LPT(float) _float_array;
    double _double;
    FRT_LPT(double) _double_array;
    FRT_StringValue _string;
    FRT_LPT(FRT_StringValue) _string_array;
    FRT_DataValue _data;
    FRT_LPT(FRT_DataValue) _data_array;
};

class FRT_Values {
public:
    using Stash = vespalib::Stash;
    using Alloc = vespalib::alloc::Alloc;

private:
    uint32_t       _maxValues;
    uint32_t       _numValues;
    char*          _typeString;
    FRT_Value*     _values;
    fnet::BlobRef* _blobs;
    Stash&         _stash;

public:
    FRT_Values(const FRT_Values&) = delete;
    FRT_Values& operator=(const FRT_Values&) = delete;
    FRT_Values(Stash& stash);
    ~FRT_Values();

    void DiscardBlobs();

    void Reset() {
        DiscardBlobs();
        _maxValues = 0;
        _numValues = 0;
        _typeString = nullptr;
        _values = nullptr;
    }

    void EnsureFree(uint32_t need = 1);

    void AddInt8(uint8_t value) {
        EnsureFree();
        _values[_numValues]._intval8 = value;
        _typeString[_numValues++] = FRT_VALUE_INT8;
    }

    uint8_t* AddInt8Array(uint32_t len);

    void AddInt8Array(const uint8_t* array, uint32_t len);

    void AddInt8ArrayRef(uint8_t* array, uint32_t len) {
        EnsureFree();
        _values[_numValues]._int8_array._pt = array;
        _values[_numValues]._int8_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT8_ARRAY;
    }

    void AddInt16(uint16_t value) {
        EnsureFree();
        _values[_numValues]._intval16 = value;
        _typeString[_numValues++] = FRT_VALUE_INT16;
    }

    uint16_t* AddInt16Array(uint32_t len);
    void      AddInt16Array(const uint16_t* array, uint32_t len);

    void AddInt16ArrayRef(uint16_t* array, uint32_t len) {
        EnsureFree();
        _values[_numValues]._int16_array._pt = array;
        _values[_numValues]._int16_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT16_ARRAY;
    }

    void AddInt32(uint32_t value) {
        EnsureFree();
        _values[_numValues]._intval32 = value;
        _typeString[_numValues++] = FRT_VALUE_INT32;
    }

    uint32_t* AddInt32Array(uint32_t len);

    void AddInt32Array(const uint32_t* array, uint32_t len);

    void AddInt32ArrayRef(uint32_t* array, uint32_t len) {
        EnsureFree();
        _values[_numValues]._int32_array._pt = array;
        _values[_numValues]._int32_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT32_ARRAY;
    }

    void AddInt64(uint64_t value) {
        EnsureFree();
        _values[_numValues]._intval64 = value;
        _typeString[_numValues++] = FRT_VALUE_INT64;
    }

    uint64_t* AddInt64Array(uint32_t len);
    void      AddInt64Array(const uint64_t* array, uint32_t len);

    void AddInt64ArrayRef(uint64_t* array, uint32_t len) {
        EnsureFree();
        _values[_numValues]._int64_array._pt = array;
        _values[_numValues]._int64_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT64_ARRAY;
    }

    void AddFloat(float value) {
        EnsureFree();
        _values[_numValues]._float = value;
        _typeString[_numValues++] = FRT_VALUE_FLOAT;
    }

    float* AddFloatArray(uint32_t len);

    void AddFloatArray(const float* array, uint32_t len);

    void AddFloatArrayRef(float* array, uint32_t len) {
        EnsureFree();
        _values[_numValues]._float_array._pt = array;
        _values[_numValues]._float_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_FLOAT_ARRAY;
    }

    void AddDouble(double value) {
        EnsureFree();
        _values[_numValues]._double = value;
        _typeString[_numValues++] = FRT_VALUE_DOUBLE;
    }

    double* AddDoubleArray(uint32_t len);
    void    AddDoubleArray(const double* array, uint32_t len);

    void AddDoubleArrayRef(double* array, uint32_t len) {
        EnsureFree();
        _values[_numValues]._double_array._pt = array;
        _values[_numValues]._double_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_DOUBLE_ARRAY;
    }

    void AddString(const char* str, uint32_t len);
    void AddString(const char* str) { AddString(str, strlen(str)); }

    char*            AddString(uint32_t len);
    FRT_StringValue* AddStringArray(uint32_t len);
    void             AddSharedData(FRT_ISharedBlob* blob);
    void             AddData(Alloc&& buf, uint32_t len);
    void             AddData(vespalib::DataBuffer&& buf);
    void             AddData(const char* buf, uint32_t len);
    char*            AddData(uint32_t len);
    FRT_DataValue*   AddDataArray(uint32_t len);

    void SetString(FRT_StringValue* value, const char* str, uint32_t len);
    void SetString(FRT_StringValue* value, const char* str);
    void SetData(FRT_DataValue* value, const char* buf, uint32_t len);

    uint32_t         GetNumValues() { return _numValues; }
    const char*      GetTypeString() { return _typeString; }
    FRT_Value&       GetValue(uint32_t idx) { return _values[idx]; }
    FRT_Value&       operator[](uint32_t idx) { return _values[idx]; }
    const FRT_Value& operator[](uint32_t idx) const { return _values[idx]; }
    uint32_t         GetType(uint32_t idx) { return _typeString[idx]; }
    void             Print(uint32_t indent = 0);
    uint32_t         GetLength();
    bool             DecodeCopy(FNET_DataBuffer* dst, uint32_t len);
    bool             DecodeBig(FNET_DataBuffer* dst, uint32_t len);
    bool             DecodeLittle(FNET_DataBuffer* dst, uint32_t len);
    void             EncodeCopy(FNET_DataBuffer* dst);
    void             EncodeBig(FNET_DataBuffer* dst);
    bool             Equals(FRT_Values* values);
    static void      Print(FRT_Value value, uint32_t type, uint32_t indent = 0);
    static bool      Equals(FRT_Value a, FRT_Value b, uint32_t type);
    static bool      Equals(FRT_Value a, uint32_t a_type, FRT_Value b, uint32_t b_type);
    static bool      CheckTypes(const char* spec, const char* actual);
};
