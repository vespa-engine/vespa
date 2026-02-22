// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "values.h"

#include <vespa/fnet/databuffer.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <cassert>

static_assert(sizeof(uint8_t) == 1, "uint8_t must be 1 byte.");
static_assert(sizeof(float) == sizeof(uint32_t), "float must be same size as uint32_t");
static_assert(sizeof(double) == sizeof(uint64_t), "double must be same size as uint64_t");

constexpr size_t SHARED_LIMIT = 1024;

namespace fnet {

char* copyString(char* dst, const char* src, size_t len) {
    if (len != 0) [[likely]] {
        memcpy(dst, src, len);
    }
    dst[len] = '\0';
    return dst;
}

char* copyData(char* dst, const void* src, size_t len) {
    if (len != 0) [[likely]] {
        memcpy(dst, src, len);
    }
    return dst;
}

using vespalib::alloc::Alloc;
class LocalBlob : public FRT_ISharedBlob {
public:
    LocalBlob(Alloc data, uint32_t len) noexcept : _data(std::move(data)), _len(len) {}
    LocalBlob(const char* data, uint32_t len);
    LocalBlob(const LocalBlob&) = delete;
    LocalBlob&  operator=(const LocalBlob&) = delete;
    void        addRef() override {}
    void        subRef() override { Alloc().swap(_data); }
    uint32_t    getLen() override { return _len; }
    const char* getData() override { return static_cast<const char*>(_data.get()); }
    char*       getInternalData() { return static_cast<char*>(_data.get()); }

private:
    Alloc    _data;
    uint32_t _len;
};

struct BlobRef {
    FRT_DataValue*   _value; // for blob inside data array
    uint32_t         _idx;   // for blob as single data value
    FRT_ISharedBlob* _blob;  // interface to shared data
    BlobRef*         _next;  // next in list

    BlobRef(FRT_DataValue* value, uint32_t idx, FRT_ISharedBlob* blob, BlobRef* next)
        : _value(value), _idx(idx), _blob(blob), _next(next) {
        blob->addRef();
    }
    BlobRef(const BlobRef&) = delete;
    BlobRef& operator=(const BlobRef&) = delete;
    ~BlobRef() { discard(); }
    void discard() {
        if (_blob != nullptr) {
            _blob->subRef();
            _blob = nullptr;
        }
    }
};

} // namespace fnet

using fnet::BlobRef;
using fnet::LocalBlob;

FRT_Values::FRT_Values(Stash& stash)
    : _maxValues(0), _numValues(0), _typeString(nullptr), _values(nullptr), _blobs(nullptr), _stash(stash) {}

FRT_Values::~FRT_Values() = default;

LocalBlob::LocalBlob(const char* data, uint32_t len) : _data(Alloc::alloc(len)), _len(len) {
    if (data != nullptr) {
        memcpy(_data.get(), data, len);
    }
}

void FRT_Values::DiscardBlobs() {
    while (_blobs != nullptr) {
        BlobRef* ref = _blobs;
        _blobs = ref->_next;
        FRT_ISharedBlob* blob = ref->_blob;
        FRT_DataValue*   value = ref->_value;
        if (value == nullptr) {
            uint32_t idx = ref->_idx;
            assert(_numValues > idx);
            assert(_typeString[idx] == 'x');
            value = &_values[idx]._data;
        }
        if ((value->_buf == blob->getData()) && (value->_len == blob->getLen())) {
            value->_buf = nullptr;
            value->_len = 0;
        }
        ref->discard();
    }
}

void FRT_Values::EnsureFree(uint32_t need) {
    if (_numValues + need <= _maxValues)
        return;

    uint32_t cnt = _maxValues * 2;
    if (cnt < _numValues + need)
        cnt = _numValues + need;
    if (cnt < 16)
        cnt = 16;

    char* types = (char*)_stash.alloc(cnt + 1);
    if (_numValues > 0) {
        assert(_typeString != nullptr);
        memcpy(types, _typeString, _numValues);
    }
    memset(types + _numValues, FRT_VALUE_NONE, cnt + 1 - _numValues);
    FRT_Value* values = (FRT_Value*)(void*)_stash.alloc(cnt * sizeof(FRT_Value));
    if (_numValues > 0) {
        assert(_values != nullptr);
        memcpy(values, _values, _numValues * sizeof(FRT_Value));
    }
    _maxValues = cnt;
    _typeString = types;
    _values = values;
}

uint8_t* FRT_Values::AddInt8Array(uint32_t len) {
    EnsureFree();
    uint8_t* ret = (uint8_t*)_stash.alloc(len * sizeof(uint8_t));
    _values[_numValues]._int8_array._pt = ret;
    _values[_numValues]._int8_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_INT8_ARRAY;
    return ret;
}

void FRT_Values::AddInt8Array(const uint8_t* array, uint32_t len) {
    EnsureFree();
    uint8_t* pt = (uint8_t*)_stash.alloc(len * sizeof(uint8_t));
    _values[_numValues]._int8_array._pt = pt;
    _values[_numValues]._int8_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_INT8_ARRAY;
    if (len != 0) [[likely]] {
        memcpy(pt, array, len * sizeof(uint8_t));
    }
}

uint16_t* FRT_Values::AddInt16Array(uint32_t len) {
    EnsureFree();
    uint16_t* ret = (uint16_t*)(void*)_stash.alloc(len * sizeof(uint16_t));
    _values[_numValues]._int16_array._pt = ret;
    _values[_numValues]._int16_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_INT16_ARRAY;
    return ret;
}

void FRT_Values::AddInt16Array(const uint16_t* array, uint32_t len) {
    EnsureFree();
    uint16_t* pt = (uint16_t*)(void*)_stash.alloc(len * sizeof(uint16_t));
    _values[_numValues]._int16_array._pt = pt;
    _values[_numValues]._int16_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_INT16_ARRAY;
    if (len != 0) [[likely]] {
        memcpy(pt, array, len * sizeof(uint16_t));
    }
}

uint32_t* FRT_Values::AddInt32Array(uint32_t len) {
    EnsureFree();
    uint32_t* ret = (uint32_t*)(void*)_stash.alloc(len * sizeof(uint32_t));
    _values[_numValues]._int32_array._pt = ret;
    _values[_numValues]._int32_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_INT32_ARRAY;
    return ret;
}

void FRT_Values::AddInt32Array(const uint32_t* array, uint32_t len) {
    EnsureFree();
    uint32_t* pt = (uint32_t*)(void*)_stash.alloc(len * sizeof(uint32_t));
    _values[_numValues]._int32_array._pt = pt;
    _values[_numValues]._int32_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_INT32_ARRAY;
    if (len != 0) [[likely]] {
        memcpy(pt, array, len * sizeof(uint32_t));
    }
}

uint64_t* FRT_Values::AddInt64Array(uint32_t len) {
    EnsureFree();
    uint64_t* ret = (uint64_t*)(void*)_stash.alloc(len * sizeof(uint64_t));
    _values[_numValues]._int64_array._pt = ret;
    _values[_numValues]._int64_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_INT64_ARRAY;
    return ret;
}

void FRT_Values::AddInt64Array(const uint64_t* array, uint32_t len) {
    EnsureFree();
    uint64_t* pt = (uint64_t*)(void*)_stash.alloc(len * sizeof(uint64_t));
    _values[_numValues]._int64_array._pt = pt;
    _values[_numValues]._int64_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_INT64_ARRAY;
    if (len != 0) [[likely]] {
        memcpy(pt, array, len * sizeof(uint64_t));
    }
}

float* FRT_Values::AddFloatArray(uint32_t len) {
    EnsureFree();
    float* ret = (float*)(void*)_stash.alloc(len * sizeof(float));
    _values[_numValues]._float_array._pt = ret;
    _values[_numValues]._float_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_FLOAT_ARRAY;
    return ret;
}

void FRT_Values::AddFloatArray(const float* array, uint32_t len) {
    EnsureFree();
    float* pt = (float*)(void*)_stash.alloc(len * sizeof(float));
    _values[_numValues]._float_array._pt = pt;
    _values[_numValues]._float_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_FLOAT_ARRAY;
    if (len != 0) [[likely]] {
        memcpy(pt, array, len * sizeof(float));
    }
}

double* FRT_Values::AddDoubleArray(uint32_t len) {
    EnsureFree();
    double* ret = (double*)(void*)_stash.alloc(len * sizeof(double));
    _values[_numValues]._double_array._pt = ret;
    _values[_numValues]._double_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_DOUBLE_ARRAY;
    return ret;
}

void FRT_Values::AddDoubleArray(const double* array, uint32_t len) {
    EnsureFree();
    double* pt = (double*)(void*)_stash.alloc(len * sizeof(double));
    _values[_numValues]._double_array._pt = pt;
    _values[_numValues]._double_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_DOUBLE_ARRAY;
    if (len != 0) [[likely]] {
        memcpy(pt, array, len * sizeof(double));
    }
}

void FRT_Values::AddString(const char* str, uint32_t len) {
    EnsureFree();
    _values[_numValues]._string._str = fnet::copyString(_stash.alloc(len + 1), str, len);
    _values[_numValues]._string._len = len;
    _typeString[_numValues++] = FRT_VALUE_STRING;
}

char* FRT_Values::AddString(uint32_t len) {
    EnsureFree();
    char* ret = (char*)_stash.alloc(len + 1);
    _values[_numValues]._string._str = ret;
    _values[_numValues]._string._len = len;
    _typeString[_numValues++] = FRT_VALUE_STRING;
    return ret;
}

FRT_StringValue* FRT_Values::AddStringArray(uint32_t len) {
    EnsureFree();
    FRT_StringValue* ret = (FRT_StringValue*)(void*)_stash.alloc(len * sizeof(FRT_StringValue));
    _values[_numValues]._string_array._pt = ret;
    _values[_numValues]._string_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_STRING_ARRAY;
    return ret;
}

void FRT_Values::AddSharedData(FRT_ISharedBlob* blob) {
    EnsureFree();
    _blobs = &_stash.create<BlobRef>(nullptr, _numValues, blob, _blobs);
    _values[_numValues]._data._buf = const_cast<char*>(blob->getData());
    _values[_numValues]._data._len = blob->getLen();
    _typeString[_numValues++] = FRT_VALUE_DATA;
}

void FRT_Values::AddData(vespalib::alloc::Alloc&& buf, uint32_t len) {
    AddSharedData(&_stash.create<LocalBlob>(std::move(buf), len));
}

void FRT_Values::AddData(vespalib::DataBuffer&& buf) {
    const auto len = buf.getDataLen();
    AddSharedData(&_stash.create<LocalBlob>(std::move(buf).stealBuffer(), len));
}

void FRT_Values::AddData(const char* buf, uint32_t len) {
    if (len > SHARED_LIMIT) {
        return AddSharedData(&_stash.create<LocalBlob>(buf, len));
    }
    EnsureFree();
    _values[_numValues]._data._buf = fnet::copyData(_stash.alloc(len), buf, len);
    _values[_numValues]._data._len = len;
    _typeString[_numValues++] = FRT_VALUE_DATA;
}

char* FRT_Values::AddData(uint32_t len) {
    if (len > SHARED_LIMIT) {
        LocalBlob* blob = &_stash.create<LocalBlob>(nullptr, len);
        AddSharedData(blob);
        return blob->getInternalData();
    }
    EnsureFree();
    char* ret = (char*)_stash.alloc(len);
    _values[_numValues]._data._buf = ret;
    _values[_numValues]._data._len = len;
    _typeString[_numValues++] = FRT_VALUE_DATA;
    return ret;
}

FRT_DataValue* FRT_Values::AddDataArray(uint32_t len) {
    EnsureFree();
    FRT_DataValue* ret = (FRT_DataValue*)(void*)_stash.alloc(len * sizeof(FRT_DataValue));
    _values[_numValues]._data_array._pt = ret;
    _values[_numValues]._data_array._len = len;
    _typeString[_numValues++] = FRT_VALUE_DATA_ARRAY;
    return ret;
}

void FRT_Values::SetString(FRT_StringValue* value, const char* str, uint32_t len) {
    value->_str = fnet::copyString(_stash.alloc(len + 1), str, len);
    value->_len = len;
}

void FRT_Values::SetString(FRT_StringValue* value, const char* str) { SetString(value, str, strlen(str)); }

void FRT_Values::SetData(FRT_DataValue* value, const char* buf, uint32_t len) {
    char* mybuf = nullptr;
    if (len > SHARED_LIMIT) {
        LocalBlob* blob = &_stash.create<LocalBlob>(buf, len);
        _blobs = &_stash.create<BlobRef>(value, 0, blob, _blobs);
        mybuf = blob->getInternalData();
    } else {
        mybuf = fnet::copyData(_stash.alloc(len), buf, len);
    }
    value->_buf = mybuf;
    value->_len = len;
}

void FRT_Values::Print(uint32_t indent) {
    printf("%*sFRT_Values {\n", indent, "");
    printf("%*s  [%s]\n", indent, "", (_numValues > 0) ? _typeString : "(Empty)");

    const char* p = _typeString;
    for (uint32_t i = 0; i < _numValues; i++, p++) {
        Print(_values[i], *p, indent + 2);
    }
    printf("%*s}\n", indent, "");
}

uint32_t FRT_Values::GetLength() {
    uint32_t    numValues = _numValues;
    const char* p = _typeString;
    uint32_t    len = sizeof(uint32_t) + numValues;
    for (uint32_t i = 0; i < numValues; i++, p++) {

        switch (*p) {

        case FRT_VALUE_INT8:
            len += sizeof(uint8_t);
            break;

        case FRT_VALUE_INT8_ARRAY:
            len += (sizeof(uint32_t) + _values[i]._int8_array._len * sizeof(uint8_t));
            break;

        case FRT_VALUE_INT16:
            len += sizeof(uint16_t);
            break;

        case FRT_VALUE_INT16_ARRAY:
            len += (sizeof(uint32_t) + _values[i]._int16_array._len * sizeof(uint16_t));
            break;

        case FRT_VALUE_INT32:
            len += sizeof(uint32_t);
            break;

        case FRT_VALUE_INT32_ARRAY:
            len += (sizeof(uint32_t) + _values[i]._int32_array._len * sizeof(uint32_t));
            break;

        case FRT_VALUE_INT64:
            len += sizeof(uint64_t);
            break;

        case FRT_VALUE_INT64_ARRAY:
            len += (sizeof(uint32_t) + _values[i]._int64_array._len * sizeof(uint64_t));
            break;

        case FRT_VALUE_FLOAT:
            len += sizeof(float);
            break;

        case FRT_VALUE_FLOAT_ARRAY:
            len += (sizeof(uint32_t) + _values[i]._float_array._len * sizeof(float));
            break;

        case FRT_VALUE_DOUBLE:
            len += sizeof(double);
            break;

        case FRT_VALUE_DOUBLE_ARRAY:
            len += (sizeof(uint32_t) + _values[i]._double_array._len * sizeof(double));
            break;

        case FRT_VALUE_STRING:
            len += sizeof(uint32_t) + _values[i]._string._len;
            break;

        case FRT_VALUE_STRING_ARRAY: {
            len += (sizeof(uint32_t) + _values[i]._string_array._len * sizeof(uint32_t));

            uint32_t         num = _values[i]._string_array._len;
            FRT_StringValue* pt = _values[i]._string_array._pt;

            for (; num > 0; num--, pt++)
                len += pt->_len;
        } break;

        case FRT_VALUE_DATA:
            len += sizeof(uint32_t) + _values[i]._data._len;
            break;

        case FRT_VALUE_DATA_ARRAY: {
            len += (sizeof(uint32_t) + _values[i]._data_array._len * sizeof(uint32_t));

            uint32_t       num = _values[i]._data_array._len;
            FRT_DataValue* pt = _values[i]._data_array._pt;

            for (; num > 0; num--, pt++)
                len += pt->_len;
        } break;

        default:
            assert(false);
        }
    }
    return len;
}

bool FRT_Values::DecodeCopy(FNET_DataBuffer* src, uint32_t len) {
    uint32_t    numValues;
    const char* typeString;
    const char* p;
    uint32_t    i;

    if (len < sizeof(uint32_t))
        goto error;
    src->ReadBytes(&numValues, sizeof(numValues));
    len -= sizeof(uint32_t);
    EnsureFree(numValues);

    if (len < numValues)
        goto error;
    typeString = src->GetData();
    src->DataToDead(numValues);
    len -= numValues;

    p = typeString;
    for (i = 0; i < numValues; i++, p++) {

        switch (*p) {

        case FRT_VALUE_INT8:
            if (len < sizeof(uint8_t))
                goto error;
            AddInt8(src->ReadInt8());
            len -= sizeof(uint8_t);
            break;

        case FRT_VALUE_INT8_ARRAY: {
            uint32_t arrlen;
            uint8_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            src->ReadBytes(&arrlen, sizeof(arrlen));
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(uint8_t))
                goto error;
            len -= arrlen * sizeof(uint8_t);
            arr = AddInt8Array(arrlen);
            src->ReadBytes(arr, arrlen);
        } break;

        case FRT_VALUE_INT16: {
            uint16_t tmp;

            if (len < sizeof(uint16_t))
                goto error;
            src->ReadBytes(&tmp, sizeof(tmp));
            AddInt16(tmp);
            len -= sizeof(uint16_t);
        } break;

        case FRT_VALUE_INT16_ARRAY: {
            uint32_t  arrlen;
            uint16_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            src->ReadBytes(&arrlen, sizeof(arrlen));
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(uint16_t))
                goto error;
            len -= arrlen * sizeof(uint16_t);
            arr = AddInt16Array(arrlen);
            src->ReadBytes(arr, arrlen * sizeof(uint16_t));
        } break;

        case FRT_VALUE_INT32: {
            uint32_t tmp;

            if (len < sizeof(uint32_t))
                goto error;
            src->ReadBytes(&tmp, sizeof(tmp));
            AddInt32(tmp);
            len -= sizeof(uint32_t);
        } break;

        case FRT_VALUE_INT32_ARRAY: {
            uint32_t  arrlen;
            uint32_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            src->ReadBytes(&arrlen, sizeof(arrlen));
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(uint32_t))
                goto error;
            len -= arrlen * sizeof(uint32_t);
            arr = AddInt32Array(arrlen);
            src->ReadBytes(arr, arrlen * sizeof(uint32_t));
        } break;

        case FRT_VALUE_INT64: {
            uint64_t tmp;

            if (len < sizeof(uint64_t))
                goto error;
            src->ReadBytes(&tmp, sizeof(tmp));
            AddInt64(tmp);
            len -= sizeof(uint64_t);
        } break;

        case FRT_VALUE_INT64_ARRAY: {
            uint32_t  arrlen;
            uint64_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            src->ReadBytes(&arrlen, sizeof(arrlen));
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(uint64_t))
                goto error;
            len -= arrlen * sizeof(uint64_t);
            arr = AddInt64Array(arrlen);
            src->ReadBytes(arr, arrlen * sizeof(uint64_t));
        } break;

        case FRT_VALUE_FLOAT: {
            union {
                uint32_t INT32;
                float    FLOAT;
            } val;
            if (len < sizeof(float))
                goto error;
            src->ReadBytes(&(val.INT32), sizeof(uint32_t));
            AddFloat(val.FLOAT);
            len -= sizeof(float);
        } break;

        case FRT_VALUE_FLOAT_ARRAY: {
            uint32_t  arrlen;
            uint32_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            src->ReadBytes(&arrlen, sizeof(arrlen));
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(float))
                goto error;
            len -= arrlen * sizeof(float);
            arr = (uint32_t*)AddFloatArray(arrlen);
            src->ReadBytes(arr, arrlen * sizeof(uint32_t));
        } break;

        case FRT_VALUE_DOUBLE: {
            union {
                uint64_t INT64;
                double   DOUBLE;
            } val;
            if (len < sizeof(double))
                goto error;
            src->ReadBytes(&(val.INT64), sizeof(uint64_t));
            AddDouble(val.DOUBLE);
            len -= sizeof(double);
        } break;

        case FRT_VALUE_DOUBLE_ARRAY: {
            uint32_t  arrlen;
            uint64_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            src->ReadBytes(&arrlen, sizeof(arrlen));
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(double))
                goto error;
            len -= arrlen * sizeof(double);
            arr = (uint64_t*)AddDoubleArray(arrlen);
            src->ReadBytes(arr, arrlen * sizeof(uint64_t));
        } break;

        case FRT_VALUE_STRING: {
            if (len < sizeof(uint32_t))
                goto error;
            uint32_t slen;
            src->ReadBytes(&slen, sizeof(slen));
            len -= sizeof(uint32_t);
            if (len < slen)
                goto error;
            AddString(src->GetData(), slen);
            src->DataToDead(slen);
            len -= slen;
        } break;

        case FRT_VALUE_STRING_ARRAY: {
            uint32_t         arrlen;
            FRT_StringValue* arr;

            if (len < sizeof(uint32_t))
                goto error;
            src->ReadBytes(&arrlen, sizeof(arrlen));
            len -= sizeof(uint32_t);
            arr = AddStringArray(arrlen);
            for (; arrlen > 0; arrlen--, arr++) {
                if (len < sizeof(uint32_t))
                    goto error;
                src->ReadBytes(&(arr->_len), sizeof(uint32_t));
                len -= sizeof(uint32_t);
                if (len < arr->_len)
                    goto error;
                SetString(arr, src->GetData(), arr->_len);
                src->DataToDead(arr->_len);
                len -= arr->_len;
            }
        } break;

        case FRT_VALUE_DATA: {
            if (len < sizeof(uint32_t))
                goto error;
            uint32_t dlen;
            src->ReadBytes(&dlen, sizeof(dlen));
            len -= sizeof(uint32_t);
            if (len < dlen)
                goto error;
            AddData(src->GetData(), dlen);
            src->DataToDead(dlen);
            len -= dlen;
        } break;

        case FRT_VALUE_DATA_ARRAY: {
            uint32_t       arrlen;
            FRT_DataValue* arr;

            if (len < sizeof(uint32_t))
                goto error;
            src->ReadBytes(&arrlen, sizeof(arrlen));
            len -= sizeof(uint32_t);
            arr = AddDataArray(arrlen);
            for (; arrlen > 0; arrlen--, arr++) {
                if (len < sizeof(uint32_t))
                    goto error;
                src->ReadBytes(&(arr->_len), sizeof(uint32_t));
                len -= sizeof(uint32_t);
                if (len < arr->_len)
                    goto error;
                SetData(arr, src->GetData(), arr->_len);
                src->DataToDead(arr->_len);
                len -= arr->_len;
            }
        } break;

        default:
            goto error;
        }
    }

    if (len != 0)
        goto error;
    if (numValues != 0 && strncmp(typeString, _typeString, numValues) != 0)
        goto error;
    return true;

error:
    src->DataToDead(len);
    return false;
}

bool FRT_Values::DecodeBig(FNET_DataBuffer* src, uint32_t len) {
    uint32_t    numValues;
    const char* typeString;
    const char* p;
    uint32_t    i;

    if (len < sizeof(uint32_t))
        goto error;
    numValues = src->ReadInt32();
    len -= sizeof(uint32_t);
    EnsureFree(numValues);

    if (len < numValues)
        goto error;
    typeString = src->GetData();
    src->DataToDead(numValues);
    len -= numValues;

    p = typeString;
    for (i = 0; i < numValues; i++, p++) {

        switch (*p) {

        case FRT_VALUE_INT8:
            if (len < sizeof(uint8_t))
                goto error;
            AddInt8(src->ReadInt8());
            len -= sizeof(uint8_t);
            break;

        case FRT_VALUE_INT8_ARRAY: {
            uint32_t arrlen;
            uint8_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32();
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(uint8_t))
                goto error;
            len -= arrlen * sizeof(uint8_t);
            arr = AddInt8Array(arrlen);
            src->ReadBytes(arr, arrlen);
        } break;

        case FRT_VALUE_INT16:
            if (len < sizeof(uint16_t))
                goto error;
            AddInt16(src->ReadInt16());
            len -= sizeof(uint16_t);
            break;

        case FRT_VALUE_INT16_ARRAY: {
            uint32_t  arrlen;
            uint16_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32();
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(uint16_t))
                goto error;
            len -= arrlen * sizeof(uint16_t);
            arr = AddInt16Array(arrlen);
            for (; arrlen > 0; arrlen--, arr++)
                *arr = src->ReadInt16();
        } break;

        case FRT_VALUE_INT32:
            if (len < sizeof(uint32_t))
                goto error;
            AddInt32(src->ReadInt32());
            len -= sizeof(uint32_t);
            break;

        case FRT_VALUE_INT32_ARRAY: {
            uint32_t  arrlen;
            uint32_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32();
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(uint32_t))
                goto error;
            len -= arrlen * sizeof(uint32_t);
            arr = AddInt32Array(arrlen);
            for (; arrlen > 0; arrlen--, arr++)
                *arr = src->ReadInt32();
        } break;

        case FRT_VALUE_INT64:
            if (len < sizeof(uint64_t))
                goto error;
            AddInt64(src->ReadInt64());
            len -= sizeof(uint64_t);
            break;

        case FRT_VALUE_INT64_ARRAY: {
            uint32_t  arrlen;
            uint64_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32();
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(uint64_t))
                goto error;
            len -= arrlen * sizeof(uint64_t);
            arr = AddInt64Array(arrlen);
            for (; arrlen > 0; arrlen--, arr++)
                *arr = src->ReadInt64();
        } break;

        case FRT_VALUE_FLOAT: {
            union {
                uint32_t INT32;
                float    FLOAT;
            } val;
            if (len < sizeof(float))
                goto error;
            val.INT32 = src->ReadInt32();
            AddFloat(val.FLOAT);
            len -= sizeof(float);
        } break;

        case FRT_VALUE_FLOAT_ARRAY: {
            uint32_t  arrlen;
            uint32_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32();
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(float))
                goto error;
            len -= arrlen * sizeof(float);
            arr = (uint32_t*)AddFloatArray(arrlen);
            for (; arrlen > 0; arrlen--, arr++)
                *arr = src->ReadInt32();
        } break;

        case FRT_VALUE_DOUBLE: {
            union {
                uint64_t INT64;
                double   DOUBLE;
            } val;
            if (len < sizeof(double))
                goto error;
            val.INT64 = src->ReadInt64();
            AddDouble(val.DOUBLE);
            len -= sizeof(double);
        } break;

        case FRT_VALUE_DOUBLE_ARRAY: {
            uint32_t  arrlen;
            uint64_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32();
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(double))
                goto error;
            len -= arrlen * sizeof(double);
            arr = (uint64_t*)AddDoubleArray(arrlen);
            for (; arrlen > 0; arrlen--, arr++)
                *arr = src->ReadInt64();
        } break;

        case FRT_VALUE_STRING: {
            if (len < sizeof(uint32_t))
                goto error;
            uint32_t slen = src->ReadInt32();
            len -= sizeof(uint32_t);
            if (len < slen)
                goto error;
            AddString(src->GetData(), slen);
            src->DataToDead(slen);
            len -= slen;
        } break;

        case FRT_VALUE_STRING_ARRAY: {
            uint32_t         arrlen;
            FRT_StringValue* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32();
            len -= sizeof(uint32_t);
            arr = AddStringArray(arrlen);
            for (; arrlen > 0; arrlen--, arr++) {
                if (len < sizeof(uint32_t))
                    goto error;
                arr->_len = src->ReadInt32();
                len -= sizeof(uint32_t);
                if (len < arr->_len)
                    goto error;
                SetString(arr, src->GetData(), arr->_len);
                src->DataToDead(arr->_len);
                len -= arr->_len;
            }
        } break;

        case FRT_VALUE_DATA: {
            if (len < sizeof(uint32_t))
                goto error;
            uint32_t dlen = src->ReadInt32();
            len -= sizeof(uint32_t);
            if (len < dlen)
                goto error;
            AddData(src->GetData(), dlen);
            src->DataToDead(dlen);
            len -= dlen;
        } break;

        case FRT_VALUE_DATA_ARRAY: {
            uint32_t       arrlen;
            FRT_DataValue* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32();
            len -= sizeof(uint32_t);
            arr = AddDataArray(arrlen);
            for (; arrlen > 0; arrlen--, arr++) {
                if (len < sizeof(uint32_t))
                    goto error;
                arr->_len = src->ReadInt32();
                len -= sizeof(uint32_t);
                if (len < arr->_len)
                    goto error;
                SetData(arr, src->GetData(), arr->_len);
                src->DataToDead(arr->_len);
                len -= arr->_len;
            }
        } break;

        default:
            goto error;
        }
    }

    if (len != 0)
        goto error;
    if ((numValues > 0) && strncmp(typeString, _typeString, numValues) != 0)
        goto error;
    return true;

error:
    src->DataToDead(len);
    return false;
}

bool FRT_Values::DecodeLittle(FNET_DataBuffer* src, uint32_t len) {
    uint32_t    numValues;
    const char* typeString;
    const char* p;
    uint32_t    i;

    if (len < sizeof(uint32_t))
        goto error;
    numValues = src->ReadInt32Reverse();
    len -= sizeof(uint32_t);
    EnsureFree(numValues);

    if (len < numValues)
        goto error;
    typeString = src->GetData();
    src->DataToDead(numValues);
    len -= numValues;

    p = typeString;
    for (i = 0; i < numValues; i++, p++) {

        switch (*p) {

        case FRT_VALUE_INT8:
            if (len < sizeof(uint8_t))
                goto error;
            AddInt8(src->ReadInt8());
            len -= sizeof(uint8_t);
            break;

        case FRT_VALUE_INT8_ARRAY: {
            uint32_t arrlen;
            uint8_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32Reverse();
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(uint8_t))
                goto error;
            len -= arrlen * sizeof(uint8_t);
            arr = AddInt8Array(arrlen);
            src->ReadBytes(arr, arrlen);
        } break;

        case FRT_VALUE_INT16:
            if (len < sizeof(uint16_t))
                goto error;
            AddInt16(src->ReadInt16Reverse());
            len -= sizeof(uint16_t);
            break;

        case FRT_VALUE_INT16_ARRAY: {
            uint32_t  arrlen;
            uint16_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32Reverse();
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(uint16_t))
                goto error;
            len -= arrlen * sizeof(uint16_t);
            arr = AddInt16Array(arrlen);
            for (; arrlen > 0; arrlen--, arr++)
                *arr = src->ReadInt16Reverse();
        } break;

        case FRT_VALUE_INT32:
            if (len < sizeof(uint32_t))
                goto error;
            AddInt32(src->ReadInt32Reverse());
            len -= sizeof(uint32_t);
            break;

        case FRT_VALUE_INT32_ARRAY: {
            uint32_t  arrlen;
            uint32_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32Reverse();
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(uint32_t))
                goto error;
            len -= arrlen * sizeof(uint32_t);
            arr = AddInt32Array(arrlen);
            for (; arrlen > 0; arrlen--, arr++)
                *arr = src->ReadInt32Reverse();
        } break;

        case FRT_VALUE_INT64:
            if (len < sizeof(uint64_t))
                goto error;
            AddInt64(src->ReadInt64Reverse());
            len -= sizeof(uint64_t);
            break;

        case FRT_VALUE_INT64_ARRAY: {
            uint32_t  arrlen;
            uint64_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32Reverse();
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(uint64_t))
                goto error;
            len -= arrlen * sizeof(uint64_t);
            arr = AddInt64Array(arrlen);
            for (; arrlen > 0; arrlen--, arr++)
                *arr = src->ReadInt64Reverse();
        } break;

        case FRT_VALUE_FLOAT: {
            union {
                uint32_t INT32;
                float    FLOAT;
            } val;
            if (len < sizeof(float))
                goto error;
            val.INT32 = src->ReadInt32Reverse();
            AddFloat(val.FLOAT);
            len -= sizeof(float);
        } break;

        case FRT_VALUE_FLOAT_ARRAY: {
            uint32_t  arrlen;
            uint32_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32Reverse();
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(float))
                goto error;
            len -= arrlen * sizeof(float);
            arr = (uint32_t*)AddFloatArray(arrlen);
            for (; arrlen > 0; arrlen--, arr++)
                *arr = src->ReadInt32Reverse();
        } break;

        case FRT_VALUE_DOUBLE: {
            union {
                uint64_t INT64;
                double   DOUBLE;
            } val;
            if (len < sizeof(double))
                goto error;
            val.INT64 = src->ReadInt64Reverse();
            AddDouble(val.DOUBLE);
            len -= sizeof(double);
        } break;

        case FRT_VALUE_DOUBLE_ARRAY: {
            uint32_t  arrlen;
            uint64_t* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32Reverse();
            len -= sizeof(uint32_t);
            if (len < arrlen * sizeof(double))
                goto error;
            len -= arrlen * sizeof(double);
            arr = (uint64_t*)AddDoubleArray(arrlen);
            for (; arrlen > 0; arrlen--, arr++)
                *arr = src->ReadInt64Reverse();
        } break;

        case FRT_VALUE_STRING: {
            if (len < sizeof(uint32_t))
                goto error;
            uint32_t slen = src->ReadInt32Reverse();
            len -= sizeof(uint32_t);
            if (len < slen)
                goto error;
            AddString(src->GetData(), slen);
            src->DataToDead(slen);
            len -= slen;
        } break;

        case FRT_VALUE_STRING_ARRAY: {
            uint32_t         arrlen;
            FRT_StringValue* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32Reverse();
            len -= sizeof(uint32_t);
            arr = AddStringArray(arrlen);
            for (; arrlen > 0; arrlen--, arr++) {
                if (len < sizeof(uint32_t))
                    goto error;
                arr->_len = src->ReadInt32Reverse();
                len -= sizeof(uint32_t);
                if (len < arr->_len)
                    goto error;
                SetString(arr, src->GetData(), arr->_len);
                src->DataToDead(arr->_len);
                len -= arr->_len;
            }
        } break;

        case FRT_VALUE_DATA: {
            if (len < sizeof(uint32_t))
                goto error;
            uint32_t dlen = src->ReadInt32Reverse();
            len -= sizeof(uint32_t);
            if (len < dlen)
                goto error;
            AddData(src->GetData(), dlen);
            src->DataToDead(dlen);
            len -= dlen;
        } break;

        case FRT_VALUE_DATA_ARRAY: {
            uint32_t       arrlen;
            FRT_DataValue* arr;

            if (len < sizeof(uint32_t))
                goto error;
            arrlen = src->ReadInt32Reverse();
            len -= sizeof(uint32_t);
            arr = AddDataArray(arrlen);
            for (; arrlen > 0; arrlen--, arr++) {
                if (len < sizeof(uint32_t))
                    goto error;
                arr->_len = src->ReadInt32Reverse();
                len -= sizeof(uint32_t);
                if (len < arr->_len)
                    goto error;
                SetData(arr, src->GetData(), arr->_len);
                src->DataToDead(arr->_len);
                len -= arr->_len;
            }
        } break;

        default:
            goto error;
        }
    }

    if (len != 0)
        goto error;
    if (numValues != 0 && strncmp(typeString, _typeString, numValues) != 0)
        goto error;
    return true;

error:
    src->DataToDead(len);
    return false;
}

void FRT_Values::EncodeCopy(FNET_DataBuffer* dst) {
    uint32_t    numValues = _numValues;
    const char* p = _typeString;

    dst->WriteBytesFast(&numValues, sizeof(numValues));
    dst->WriteBytesFast(p, numValues);

    for (uint32_t i = 0; i < numValues; i++, p++) {

        switch (*p) {

        case FRT_VALUE_INT8:
            dst->WriteInt8Fast(_values[i]._intval8);
            break;

        case FRT_VALUE_INT8_ARRAY: {
            uint32_t len = _values[i]._int8_array._len;
            uint8_t* pt = _values[i]._int8_array._pt;

            dst->WriteBytesFast(&len, sizeof(len));
            dst->WriteBytesFast(pt, len);
        } break;

        case FRT_VALUE_INT16:
            dst->WriteBytesFast(&(_values[i]._intval16), sizeof(uint16_t));
            break;

        case FRT_VALUE_INT16_ARRAY: {
            uint32_t  len = _values[i]._int16_array._len;
            uint16_t* pt = _values[i]._int16_array._pt;

            dst->WriteBytesFast(&len, sizeof(len));
            dst->WriteBytesFast(pt, len * sizeof(uint16_t));
        } break;

        case FRT_VALUE_INT32:
            dst->WriteBytesFast(&(_values[i]._intval32), sizeof(uint32_t));
            break;

        case FRT_VALUE_INT32_ARRAY: {
            uint32_t  len = _values[i]._int32_array._len;
            uint32_t* pt = _values[i]._int32_array._pt;

            dst->WriteBytesFast(&len, sizeof(len));
            dst->WriteBytesFast(pt, len * sizeof(uint32_t));
        } break;

        case FRT_VALUE_INT64:
            dst->WriteBytesFast(&(_values[i]._intval64), sizeof(uint64_t));
            break;

        case FRT_VALUE_INT64_ARRAY: {
            uint32_t  len = _values[i]._int64_array._len;
            uint64_t* pt = _values[i]._int64_array._pt;

            dst->WriteBytesFast(&len, sizeof(len));
            dst->WriteBytesFast(pt, len * sizeof(uint64_t));
        } break;

        case FRT_VALUE_FLOAT:
            dst->WriteBytesFast(&(_values[i]._intval32), sizeof(uint32_t));
            break;

        case FRT_VALUE_FLOAT_ARRAY: {
            uint32_t  len = _values[i]._float_array._len;
            uint32_t* pt = (uint32_t*)_values[i]._float_array._pt;

            dst->WriteBytesFast(&len, sizeof(len));
            dst->WriteBytesFast(pt, len * sizeof(uint32_t));
        } break;

        case FRT_VALUE_DOUBLE:
            dst->WriteBytesFast(&(_values[i]._intval64), sizeof(uint64_t));
            break;

        case FRT_VALUE_DOUBLE_ARRAY: {
            uint32_t  len = _values[i]._double_array._len;
            uint64_t* pt = (uint64_t*)_values[i]._double_array._pt;

            dst->WriteBytesFast(&len, sizeof(len));
            dst->WriteBytesFast(pt, len * sizeof(uint64_t));
        } break;

        case FRT_VALUE_STRING:
            dst->WriteBytesFast(&(_values[i]._string._len), sizeof(uint32_t));
            dst->WriteBytesFast(_values[i]._string._str, _values[i]._string._len);
            break;

        case FRT_VALUE_STRING_ARRAY: {
            uint32_t         len = _values[i]._string_array._len;
            FRT_StringValue* pt = _values[i]._string_array._pt;

            dst->WriteBytesFast(&len, sizeof(len));
            for (; len > 0; len--, pt++) {
                dst->WriteBytesFast(&(pt->_len), sizeof(uint32_t));
                dst->WriteBytesFast(pt->_str, pt->_len);
            }
        } break;

        case FRT_VALUE_DATA:
            dst->WriteBytesFast(&(_values[i]._data._len), sizeof(uint32_t));
            dst->WriteBytesFast(_values[i]._data._buf, _values[i]._data._len);
            break;

        case FRT_VALUE_DATA_ARRAY: {
            uint32_t       len = _values[i]._data_array._len;
            FRT_DataValue* pt = _values[i]._data_array._pt;

            dst->WriteBytesFast(&len, sizeof(len));
            for (; len > 0; len--, pt++) {
                dst->WriteBytesFast(&(pt->_len), sizeof(uint32_t));
                dst->WriteBytesFast(pt->_buf, pt->_len);
            }
        } break;

        default:
            assert(false);
        }
    }
}

void FRT_Values::EncodeBig(FNET_DataBuffer* dst) {
    uint32_t    numValues = _numValues;
    const char* p = _typeString;

    dst->WriteInt32Fast(numValues);
    if (numValues == 0) {
        return; // p may be nullptr, don't try to write what does not exist.
    }
    dst->WriteBytesFast(p, numValues);

    for (uint32_t i = 0; i < numValues; i++, p++) {

        switch (*p) {

        case FRT_VALUE_INT8:
            dst->WriteInt8Fast(_values[i]._intval8);
            break;

        case FRT_VALUE_INT8_ARRAY: {
            uint32_t len = _values[i]._int8_array._len;
            uint8_t* pt = _values[i]._int8_array._pt;

            dst->WriteInt32Fast(len);
            dst->WriteBytesFast(pt, len);
        } break;

        case FRT_VALUE_INT16:
            dst->WriteInt16Fast(_values[i]._intval16);
            break;

        case FRT_VALUE_INT16_ARRAY: {
            uint32_t  len = _values[i]._int16_array._len;
            uint16_t* pt = _values[i]._int16_array._pt;

            dst->WriteInt32Fast(len);
            for (; len > 0; len--, pt++)
                dst->WriteInt16Fast(*pt);
        } break;

        case FRT_VALUE_INT32:
            dst->WriteInt32Fast(_values[i]._intval32);
            break;

        case FRT_VALUE_INT32_ARRAY: {
            uint32_t  len = _values[i]._int32_array._len;
            uint32_t* pt = _values[i]._int32_array._pt;

            dst->WriteInt32Fast(len);
            for (; len > 0; len--, pt++)
                dst->WriteInt32Fast(*pt);
        } break;

        case FRT_VALUE_INT64:
            dst->WriteInt64Fast(_values[i]._intval64);
            break;

        case FRT_VALUE_INT64_ARRAY: {
            uint32_t  len = _values[i]._int64_array._len;
            uint64_t* pt = _values[i]._int64_array._pt;

            dst->WriteInt32Fast(len);
            for (; len > 0; len--, pt++)
                dst->WriteInt64Fast(*pt);
        } break;

        case FRT_VALUE_FLOAT:
            dst->WriteInt32Fast(_values[i]._intval32);
            break;

        case FRT_VALUE_FLOAT_ARRAY: {
            uint32_t  len = _values[i]._float_array._len;
            uint32_t* pt = (uint32_t*)_values[i]._float_array._pt;

            dst->WriteInt32Fast(len);
            for (; len > 0; len--, pt++)
                dst->WriteInt32Fast(*pt);
        } break;

        case FRT_VALUE_DOUBLE:
            dst->WriteInt64Fast(_values[i]._intval64);
            break;

        case FRT_VALUE_DOUBLE_ARRAY: {
            uint32_t  len = _values[i]._double_array._len;
            uint64_t* pt = (uint64_t*)_values[i]._double_array._pt;

            dst->WriteInt32Fast(len);
            for (; len > 0; len--, pt++)
                dst->WriteInt64Fast(*pt);
        } break;

        case FRT_VALUE_STRING:
            dst->WriteInt32Fast(_values[i]._string._len);
            dst->WriteBytesFast(_values[i]._string._str, _values[i]._string._len);
            break;

        case FRT_VALUE_STRING_ARRAY: {
            uint32_t         len = _values[i]._string_array._len;
            FRT_StringValue* pt = _values[i]._string_array._pt;

            dst->WriteInt32Fast(len);
            for (; len > 0; len--, pt++) {
                dst->WriteInt32Fast(pt->_len);
                dst->WriteBytesFast(pt->_str, pt->_len);
            }
        } break;

        case FRT_VALUE_DATA:
            dst->WriteInt32Fast(_values[i]._data._len);
            dst->WriteBytesFast(_values[i]._data._buf, _values[i]._data._len);
            break;

        case FRT_VALUE_DATA_ARRAY: {
            uint32_t       len = _values[i]._data_array._len;
            FRT_DataValue* pt = _values[i]._data_array._pt;

            dst->WriteInt32Fast(len);
            for (; len > 0; len--, pt++) {
                dst->WriteInt32Fast(pt->_len);
                dst->WriteBytesFast(pt->_buf, pt->_len);
            }
        } break;

        default:
            assert(false);
        }
    }
}

bool FRT_Values::Equals(FRT_Values* values) {
    if (values->GetNumValues() != GetNumValues())
        return false;

    if (GetNumValues() == 0)
        return true;

    if (strcmp(values->GetTypeString(), GetTypeString()) != 0)
        return false;

    for (uint32_t i = 0; i < GetNumValues(); i++)
        if (!Equals(values->GetValue(i), GetValue(i), GetType(i)))
            return false;

    return true;
}

void FRT_Values::Print(FRT_Value value, uint32_t type, uint32_t indent) {
    switch (type) {

    case FRT_VALUE_INT8:
        printf("%*sint8: %u\n", indent, "", value._intval8);
        break;

    case FRT_VALUE_INT8_ARRAY: {
        uint32_t len = value._int8_array._len;
        uint8_t* pt = value._int8_array._pt;

        printf("%*sint8_array {\n", indent, "");
        for (; len > 0; len--, pt++)
            printf("%*s  int8: %u\n", indent, "", *pt);
        printf("%*s}\n", indent, "");
    } break;

    case FRT_VALUE_INT16:
        printf("%*sint16: %u\n", indent, "", value._intval16);
        break;

    case FRT_VALUE_INT16_ARRAY: {
        uint32_t  len = value._int16_array._len;
        uint16_t* pt = value._int16_array._pt;

        printf("%*sint16_array {\n", indent, "");
        for (; len > 0; len--, pt++)
            printf("%*s  int16: %u\n", indent, "", *pt);
        printf("%*s}\n", indent, "");
    } break;

    case FRT_VALUE_INT32:
        printf("%*sint32: %u\n", indent, "", value._intval32);
        break;

    case FRT_VALUE_INT32_ARRAY: {
        uint32_t  len = value._int32_array._len;
        uint32_t* pt = value._int32_array._pt;

        printf("%*sint32_array {\n", indent, "");
        for (; len > 0; len--, pt++)
            printf("%*s  int32: %u\n", indent, "", *pt);
        printf("%*s}\n", indent, "");
    } break;

    case FRT_VALUE_INT64:
        printf("%*sint64: %" PRIu64 "\n", indent, "", value._intval64);
        break;

    case FRT_VALUE_INT64_ARRAY: {
        uint32_t  len = value._int64_array._len;
        uint64_t* pt = value._int64_array._pt;

        printf("%*sint64_array {\n", indent, "");
        for (; len > 0; len--, pt++)
            printf("%*s  int64: %" PRIu64 "\n", indent, "", *pt);
        printf("%*s}\n", indent, "");
    } break;

    case FRT_VALUE_FLOAT:
        printf("%*sfloat: %f\n", indent, "", value._float);
        break;

    case FRT_VALUE_FLOAT_ARRAY: {
        uint32_t len = value._float_array._len;
        float*   pt = value._float_array._pt;

        printf("%*sfloat_array {\n", indent, "");
        for (; len > 0; len--, pt++)
            printf("%*s  float: %f\n", indent, "", *pt);
        printf("%*s}\n", indent, "");
    } break;

    case FRT_VALUE_DOUBLE:
        printf("%*sdouble: %f\n", indent, "", value._double);
        break;

    case FRT_VALUE_DOUBLE_ARRAY: {
        uint32_t len = value._double_array._len;
        double*  pt = value._double_array._pt;

        printf("%*sdouble_array {\n", indent, "");
        for (; len > 0; len--, pt++)
            printf("%*s  double: %f\n", indent, "", *pt);
        printf("%*s}\n", indent, "");
    } break;

    case FRT_VALUE_STRING:
        printf("%*sstring: %s\n", indent, "", value._string._str);
        break;

    case FRT_VALUE_STRING_ARRAY: {
        uint32_t         len = value._string_array._len;
        FRT_StringValue* pt = value._string_array._pt;

        printf("%*sstring_array {\n", indent, "");
        for (; len > 0; len--, pt++)
            printf("%*s  string: %s\n", indent, "", pt->_str);
        printf("%*s}\n", indent, "");
    } break;

    case FRT_VALUE_DATA:
        printf("%*sdata: len=%u\n", indent, "", value._data._len);
        break;

    case FRT_VALUE_DATA_ARRAY: {
        uint32_t       len = value._data_array._len;
        FRT_DataValue* pt = value._data_array._pt;

        printf("%*sdata_array {\n", indent, "");
        for (; len > 0; len--, pt++)
            printf("%*s  data: len=%u\n", indent, "", pt->_len);
        printf("%*s}\n", indent, "");
    } break;

    default:
        assert(false);
    }
}

bool FRT_Values::Equals(FRT_Value a, FRT_Value b, uint32_t type) {
    bool rc = true;

    switch (type) {

    case FRT_VALUE_INT8:
        rc = (a._intval8 == b._intval8);
        break;

    case FRT_VALUE_INT8_ARRAY: {
        uint32_t len = a._int8_array._len;
        uint8_t* pt_a = a._int8_array._pt;
        uint8_t* pt_b = b._int8_array._pt;

        for (; rc && len > 0; len--)
            rc = (*pt_a++ == *pt_b++);
    } break;

    case FRT_VALUE_INT16:
        rc = (a._intval16 == b._intval16);
        break;

    case FRT_VALUE_INT16_ARRAY: {
        uint32_t  len = a._int16_array._len;
        uint16_t* pt_a = a._int16_array._pt;
        uint16_t* pt_b = b._int16_array._pt;

        for (; rc && len > 0; len--)
            rc = (*pt_a++ == *pt_b++);
    } break;

    case FRT_VALUE_INT32:
        rc = (a._intval32 == b._intval32);
        break;

    case FRT_VALUE_INT32_ARRAY: {
        uint32_t  len = a._int32_array._len;
        uint32_t* pt_a = a._int32_array._pt;
        uint32_t* pt_b = b._int32_array._pt;

        for (; rc && len > 0; len--)
            rc = (*pt_a++ == *pt_b++);
    } break;

    case FRT_VALUE_INT64:
        rc = (a._intval64 == b._intval64);
        break;

    case FRT_VALUE_INT64_ARRAY: {
        uint32_t  len = a._int64_array._len;
        uint64_t* pt_a = a._int64_array._pt;
        uint64_t* pt_b = b._int64_array._pt;

        for (; rc && len > 0; len--)
            rc = (*pt_a++ == *pt_b++);
    } break;

    case FRT_VALUE_FLOAT:
        rc = (a._float == b._float);
        break;

    case FRT_VALUE_FLOAT_ARRAY: {
        uint32_t len = a._float_array._len;
        float*   pt_a = a._float_array._pt;
        float*   pt_b = b._float_array._pt;

        for (; rc && len > 0; len--)
            rc = (*pt_a++ == *pt_b++);
    } break;

    case FRT_VALUE_DOUBLE:
        rc = (a._double == b._double);
        break;

    case FRT_VALUE_DOUBLE_ARRAY: {
        uint32_t len = a._double_array._len;
        double*  pt_a = a._double_array._pt;
        double*  pt_b = b._double_array._pt;

        for (; rc && len > 0; len--)
            rc = (*pt_a++ == *pt_b++);
    } break;

    case FRT_VALUE_STRING:
        rc = (a._string._len == b._string._len && memcmp(a._string._str, b._string._str, a._string._len) == 0);
        break;

    case FRT_VALUE_STRING_ARRAY: {
        uint32_t         len = a._string_array._len;
        FRT_StringValue* pt_a = a._string_array._pt;
        FRT_StringValue* pt_b = b._string_array._pt;

        for (; rc && len > 0; len--, pt_a++, pt_b++)
            rc = (pt_a->_len == pt_b->_len && memcmp(pt_a->_str, pt_b->_str, pt_a->_len) == 0);
    } break;

    case FRT_VALUE_DATA:
        rc = (a._data._len == b._data._len && memcmp(a._data._buf, b._data._buf, a._data._len) == 0);
        break;

    case FRT_VALUE_DATA_ARRAY: {
        uint32_t       len = a._data_array._len;
        FRT_DataValue* pt_a = a._data_array._pt;
        FRT_DataValue* pt_b = b._data_array._pt;

        for (; rc && len > 0; len--, pt_a++, pt_b++)
            rc = (pt_a->_len == pt_b->_len && memcmp(pt_a->_buf, pt_b->_buf, pt_a->_len) == 0);
    } break;

    default:
        rc = false;
    }
    return rc;
}

bool FRT_Values::Equals(FRT_Value a, uint32_t a_type, FRT_Value b, uint32_t b_type) {
    return (a_type == b_type) ? Equals(a, b, a_type) : false;
}

bool FRT_Values::CheckTypes(const char* spec, const char* actual) {
    for (; *spec == *actual && *spec != '\0'; spec++, actual++)
        ;
    return ((*spec == *actual) || (spec[0] == '*' && spec[1] == '\0'));
}
