// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

template <typename T>
struct FRT_Array {
    uint32_t _len;
    T *_pt;
};

#define FRT_LPT(type) FRT_Array<type>

enum {
    FRT_VALUE_NONE         = '\0',
    FRT_VALUE_INT8         = 'b',
    FRT_VALUE_INT8_ARRAY   = 'B',
    FRT_VALUE_INT16        = 'h',
    FRT_VALUE_INT16_ARRAY  = 'H',
    FRT_VALUE_INT32        = 'i',
    FRT_VALUE_INT32_ARRAY  = 'I',
    FRT_VALUE_INT64        = 'l',
    FRT_VALUE_INT64_ARRAY  = 'L',
    FRT_VALUE_FLOAT        = 'f',
    FRT_VALUE_FLOAT_ARRAY  = 'F',
    FRT_VALUE_DOUBLE       = 'd',
    FRT_VALUE_DOUBLE_ARRAY = 'D',
    FRT_VALUE_STRING       = 's',
    FRT_VALUE_STRING_ARRAY = 'S',
    FRT_VALUE_DATA         = 'x',
    FRT_VALUE_DATA_ARRAY   = 'X'
};


struct FRT_StringValue
{
    uint32_t  _len;
    char     *_str;
};


struct FRT_DataValue
{
    uint32_t  _len;
    char     *_buf;
};


union FRT_Value
{
    uint8_t                   _intval8;
    FRT_LPT(uint8_t)          _int8_array;
    uint16_t                  _intval16;
    FRT_LPT(uint16_t)         _int16_array;
    uint32_t                  _intval32;
    FRT_LPT(uint32_t)         _int32_array;
    uint64_t                  _intval64;
    FRT_LPT(uint64_t)         _int64_array;
    float                     _float;
    FRT_LPT(float)            _float_array;
    double                    _double;
    FRT_LPT(double)           _double_array;
    FRT_StringValue           _string;
    FRT_LPT(FRT_StringValue)  _string_array;
    FRT_DataValue             _data;
    FRT_LPT(FRT_DataValue)    _data_array;
};


class FRT_Values
{
public:
    class LocalBlob : public FRT_ISharedBlob
    {
    public:
        LocalBlob(vespalib::DefaultAlloc data, uint32_t len) :
            _data(std::move(data)),
            _len(len)
        { }
        LocalBlob(const char *data, uint32_t len) :
            _data(len),
            _len(len)
        {
            if (data != NULL) {
                memcpy(_data.get(), data, len);
            }
        }
        void addRef() override {}
        void subRef() override { vespalib::DefaultAlloc().swap(_data); }
        uint32_t getLen() override { return _len; }
        const char *getData() override { return static_cast<const char *>(_data.get()); }
        char *getInternalData() { return static_cast<char *>(_data.get()); }
    private:
        LocalBlob(const LocalBlob &);
        LocalBlob &operator=(const LocalBlob &);

        vespalib::DefaultAlloc _data;
        uint32_t _len;
    };

    struct BlobRef
    {
        FRT_DataValue   *_value; // for blob inside data array
        uint32_t         _idx;   // for blob as single data value
        FRT_ISharedBlob *_blob;  // interface to shared data
        BlobRef         *_next;  // next in list

        BlobRef(FRT_DataValue *value, uint32_t idx, FRT_ISharedBlob *blob, BlobRef *next)
            : _value(value), _idx(idx), _blob(blob), _next(next) { blob->addRef(); }
        ~BlobRef() { _blob->subRef(); }
    private:
        BlobRef(const BlobRef &);
        BlobRef &operator=(const BlobRef &);
    };

private:
    uint32_t       _maxValues;
    uint32_t       _numValues;
    char          *_typeString;
    FRT_Value     *_values;
    BlobRef       *_blobs;
    FRT_MemoryTub *_tub;

    FRT_Values(const FRT_Values &);
    FRT_Values &operator=(const FRT_Values &);

public:
    FRT_Values(FRT_MemoryTub *tub)
        : _maxValues(0),
          _numValues(0),
          _typeString(NULL),
          _values(NULL),
          _blobs(NULL),
          _tub(tub)
    {
        assert(sizeof(uint8_t) == 1);
        assert(sizeof(float)   == sizeof(uint32_t));
        assert(sizeof(double)  == sizeof(uint64_t));
    }

    void DiscardBlobs() {
        while (_blobs != NULL) {
            BlobRef *ref = _blobs;
            _blobs = ref->_next;
            FRT_ISharedBlob *blob = ref->_blob;
            FRT_DataValue *value = ref->_value;
            if (value == NULL) {
                uint32_t idx = ref->_idx;
                assert(_numValues > idx);
                assert(_typeString[idx] == 'x');
                value = &_values[idx]._data;
            }
            if ((value->_buf == blob->getData()) && (value->_len == blob->getLen())) {
                value->_buf = NULL;
                value->_len = 0;
            }
            ref->~BlobRef();
        }
    }

    void Reset()
    {
        DiscardBlobs();
        _maxValues  = 0;
        _numValues  = 0;
        _typeString = NULL;
        _values     = NULL;
    }

    void EnsureFree(uint32_t need = 1)
    {
        if (_numValues + need <= _maxValues)
            return;

        uint32_t cnt = _maxValues * 2;
        if (cnt < _numValues + need)
            cnt = _numValues + need;
        if (cnt < 16)
            cnt = 16;

        char *types = (char *) _tub->Alloc(cnt + 1);
        memcpy(types, _typeString, _numValues);
        memset(types + _numValues, FRT_VALUE_NONE, cnt + 1 - _numValues);
        FRT_Value *values = (FRT_Value *) _tub->Alloc(cnt * sizeof(FRT_Value));
        memcpy(values, _values, _numValues * sizeof(FRT_Value));
        _maxValues  = cnt;
        _typeString = types;
        _values     = values;
    }

    void AddInt8(uint8_t value)
    {
        EnsureFree();
        _values[_numValues]._intval8 = value;
        _typeString[_numValues++] = FRT_VALUE_INT8;
    }

    uint8_t *AddInt8Array(uint32_t len)
    {
        EnsureFree();
        uint8_t *ret = (uint8_t *) _tub->Alloc(len * sizeof(uint8_t));
        _values[_numValues]._int8_array._pt = ret;
        _values[_numValues]._int8_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT8_ARRAY;
        return ret;
    }

    void AddInt8Array(const uint8_t *array, uint32_t len)
    {
        EnsureFree();
        uint8_t *pt = (uint8_t *) _tub->Alloc(len * sizeof(uint8_t));
        _values[_numValues]._int8_array._pt = pt;
        _values[_numValues]._int8_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT8_ARRAY;
        memcpy(pt, array, len * sizeof(uint8_t));
    }

    void AddInt8ArrayRef(uint8_t *array, uint32_t len)
    {
        EnsureFree();
        _values[_numValues]._int8_array._pt = array;
        _values[_numValues]._int8_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT8_ARRAY;
    }

    void AddInt16(uint16_t value)
    {
        EnsureFree();
        _values[_numValues]._intval16 = value;
        _typeString[_numValues++] = FRT_VALUE_INT16;
    }

    uint16_t *AddInt16Array(uint32_t len)
    {
        EnsureFree();
        uint16_t *ret = (uint16_t *) _tub->Alloc(len * sizeof(uint16_t));
        _values[_numValues]._int16_array._pt = ret;
        _values[_numValues]._int16_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT16_ARRAY;
        return ret;
    }

    void AddInt16Array(const uint16_t *array, uint32_t len)
    {
        EnsureFree();
        uint16_t *pt = (uint16_t *) _tub->Alloc(len * sizeof(uint16_t));
        _values[_numValues]._int16_array._pt = pt;
        _values[_numValues]._int16_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT16_ARRAY;
        memcpy(pt, array, len * sizeof(uint16_t));
    }

    void AddInt16ArrayRef(uint16_t *array, uint32_t len)
    {
        EnsureFree();
        _values[_numValues]._int16_array._pt = array;
        _values[_numValues]._int16_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT16_ARRAY;
    }

    void AddInt32(uint32_t value)
    {
        EnsureFree();
        _values[_numValues]._intval32 = value;
        _typeString[_numValues++] = FRT_VALUE_INT32;
    }

    uint32_t *AddInt32Array(uint32_t len)
    {
        EnsureFree();
        uint32_t *ret = (uint32_t *) _tub->Alloc(len * sizeof(uint32_t));
        _values[_numValues]._int32_array._pt = ret;
        _values[_numValues]._int32_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT32_ARRAY;
        return ret;
    }

    void AddInt32Array(const uint32_t *array, uint32_t len)
    {
        EnsureFree();
        uint32_t *pt = (uint32_t *) _tub->Alloc(len * sizeof(uint32_t));
        _values[_numValues]._int32_array._pt = pt;
        _values[_numValues]._int32_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT32_ARRAY;
        memcpy(pt, array, len * sizeof(uint32_t));
    }

    void AddInt32ArrayRef(uint32_t *array, uint32_t len)
    {
        EnsureFree();
        _values[_numValues]._int32_array._pt = array;
        _values[_numValues]._int32_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT32_ARRAY;
    }

    void AddInt64(uint64_t value)
    {
        EnsureFree();
        _values[_numValues]._intval64 = value;
        _typeString[_numValues++] = FRT_VALUE_INT64;
    }

    uint64_t *AddInt64Array(uint32_t len)
    {
        EnsureFree();
        uint64_t *ret = (uint64_t *) _tub->Alloc(len * sizeof(uint64_t));
        _values[_numValues]._int64_array._pt = ret;
        _values[_numValues]._int64_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT64_ARRAY;
        return ret;
    }

    void AddInt64Array(const uint64_t *array, uint32_t len)
    {
        EnsureFree();
        uint64_t *pt = (uint64_t *) _tub->Alloc(len * sizeof(uint64_t));
        _values[_numValues]._int64_array._pt = pt;
        _values[_numValues]._int64_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT64_ARRAY;
        memcpy(pt, array, len * sizeof(uint64_t));
    }

    void AddInt64ArrayRef(uint64_t *array, uint32_t len)
    {
        EnsureFree();
        _values[_numValues]._int64_array._pt = array;
        _values[_numValues]._int64_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_INT64_ARRAY;
    }

    void AddFloat(float value)
    {
        EnsureFree();
        _values[_numValues]._float = value;
        _typeString[_numValues++] = FRT_VALUE_FLOAT;
    }

    float *AddFloatArray(uint32_t len)
    {
        EnsureFree();
        float *ret = (float *) _tub->Alloc(len * sizeof(float));
        _values[_numValues]._float_array._pt = ret;
        _values[_numValues]._float_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_FLOAT_ARRAY;
        return ret;
    }

    void AddFloatArray(const float *array, uint32_t len)
    {
        EnsureFree();
        float *pt = (float *) _tub->Alloc(len * sizeof(float));
        _values[_numValues]._float_array._pt = pt;
        _values[_numValues]._float_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_FLOAT_ARRAY;
        memcpy(pt, array, len * sizeof(float));
    }

    void AddFloatArrayRef(float *array, uint32_t len)
    {
        EnsureFree();
        _values[_numValues]._float_array._pt = array;
        _values[_numValues]._float_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_FLOAT_ARRAY;
    }

    void AddDouble(double value)
    {
        EnsureFree();
        _values[_numValues]._double = value;
        _typeString[_numValues++] = FRT_VALUE_DOUBLE;
    }

    double *AddDoubleArray(uint32_t len)
    {
        EnsureFree();
        double *ret = (double *) _tub->Alloc(len * sizeof(double));
        _values[_numValues]._double_array._pt = ret;
        _values[_numValues]._double_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_DOUBLE_ARRAY;
        return ret;
    }

    void AddDoubleArray(const double *array, uint32_t len)
    {
        EnsureFree();
        double *pt = (double *) _tub->Alloc(len * sizeof(double));
        _values[_numValues]._double_array._pt = pt;
        _values[_numValues]._double_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_DOUBLE_ARRAY;
        memcpy(pt, array, len * sizeof(double));
    }

    void AddDoubleArrayRef(double *array, uint32_t len)
    {
        EnsureFree();
        _values[_numValues]._double_array._pt = array;
        _values[_numValues]._double_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_DOUBLE_ARRAY;
    }

    void AddString(const char *str, uint32_t len)
    {
        EnsureFree();
        _values[_numValues]._string._str = _tub->CopyString(str, len);
        _values[_numValues]._string._len = len;
        _typeString[_numValues++] = FRT_VALUE_STRING;
    }

    void AddString(const char *str)
    {
        AddString(str, strlen(str));
    }

    char *AddString(uint32_t len)
    {
        EnsureFree();
        char *ret = (char *) _tub->Alloc(len + 1);
        _values[_numValues]._string._str = ret;
        _values[_numValues]._string._len = len;
        _typeString[_numValues++] = FRT_VALUE_STRING;
        return ret;
    }

    FRT_StringValue *AddStringArray(uint32_t len)
    {
        EnsureFree();
        FRT_StringValue *ret = (FRT_StringValue *) _tub->Alloc(len * sizeof(FRT_StringValue));
        _values[_numValues]._string_array._pt = ret;
        _values[_numValues]._string_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_STRING_ARRAY;
        return ret;
    }

    void AddSharedData(FRT_ISharedBlob *blob)
    {
        EnsureFree();
        _blobs = new (_tub) BlobRef(NULL, _numValues, blob, _blobs);
        _values[_numValues]._data._buf = const_cast<char *>(blob->getData());
        _values[_numValues]._data._len = blob->getLen();
        _typeString[_numValues++] = FRT_VALUE_DATA;
    }

    void AddData(vespalib::DefaultAlloc buf, uint32_t len)
    {
        AddSharedData(new (_tub) LocalBlob(std::move(buf), len));
    }

    void AddData(const char *buf, uint32_t len)
    {
        if (len > FRT_MemoryTub::ALLOC_LIMIT) {
            return AddSharedData(new (_tub) LocalBlob(buf, len));
        }
        EnsureFree();
        _values[_numValues]._data._buf = _tub->CopyData(buf, len);
        _values[_numValues]._data._len = len;
        _typeString[_numValues++] = FRT_VALUE_DATA;
    }

    char *AddData(uint32_t len)
    {
        if (len > FRT_MemoryTub::ALLOC_LIMIT) {
            LocalBlob *blob = new (_tub) LocalBlob(NULL, len);
            AddSharedData(blob);
            return blob->getInternalData();
        }
        EnsureFree();
        char *ret = (char *) _tub->Alloc(len);
        _values[_numValues]._data._buf = ret;
        _values[_numValues]._data._len = len;
        _typeString[_numValues++] = FRT_VALUE_DATA;
        return ret;
    }

    FRT_DataValue *AddDataArray(uint32_t len)
    {
        EnsureFree();
        FRT_DataValue *ret = (FRT_DataValue *) _tub->Alloc(len * sizeof(FRT_DataValue));
        _values[_numValues]._data_array._pt = ret;
        _values[_numValues]._data_array._len = len;
        _typeString[_numValues++] = FRT_VALUE_DATA_ARRAY;
        return ret;
    }

    void SetString(FRT_StringValue *value,
                   const char *str, uint32_t len)
    {
        value->_str = _tub->CopyString(str, len);
        value->_len = len;
    }

    void SetString(FRT_StringValue *value,
                   const char *str)
    {
        uint32_t len = strlen(str);
        value->_str = _tub->CopyString(str, len);
        value->_len = len;
    }

    void SetData(FRT_DataValue *value,
                 const char *buf, uint32_t len)
    {
        char *mybuf = NULL;
        if (len > FRT_MemoryTub::ALLOC_LIMIT) {
            LocalBlob *blob = new (_tub) LocalBlob(buf, len);
            _blobs = new (_tub) BlobRef(value, 0, blob, _blobs);
            mybuf = blob->getInternalData();
        } else {
            mybuf = _tub->CopyData(buf, len);
        }
        value->_buf = mybuf;
        value->_len = len;
    }


    uint32_t GetNumValues() { return _numValues; }
    const char *GetTypeString() { return _typeString; }
    FRT_Value &GetValue(uint32_t idx) { return _values[idx]; }
    FRT_Value &operator [](uint32_t idx) { return _values[idx]; }
    const FRT_Value &operator [](uint32_t idx) const { return _values[idx]; }
    uint32_t GetType(uint32_t idx) { return _typeString[idx]; }
    void Print(uint32_t indent = 0);
    uint32_t GetLength();
    bool DecodeCopy(FNET_DataBuffer *dst, uint32_t len);
    bool DecodeBig(FNET_DataBuffer *dst, uint32_t len);
    bool DecodeLittle(FNET_DataBuffer *dst, uint32_t len);
    void EncodeCopy(FNET_DataBuffer *dst);
    void EncodeBig(FNET_DataBuffer *dst);
    bool Equals(FRT_Values *values);
    static void Print(FRT_Value value, uint32_t type,
                      uint32_t indent = 0);
    static bool Equals(FRT_Value a, FRT_Value b, uint32_t type);
    static bool Equals(FRT_Value a, uint32_t a_type,
                       FRT_Value b, uint32_t b_type);
    static bool CheckTypes(const char *spec, const char *actual);
};

