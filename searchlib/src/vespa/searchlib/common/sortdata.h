// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::common {

class SortData
{
public:
    struct Ref
    {
        const char *_buf;
        uint32_t    _len;
    };

    static uint32_t GetSize(uint32_t        hitcnt,
                            const uint32_t *sortIndex);

    static bool Equals(uint32_t        hitcnt,
                       const uint32_t *sortIndex_1,
                       const char     *sortData_1,
                       const uint32_t *sortIndex_2,
                       const char     *sortData_2);

    // NB: first element of sortIndex_dst must be set
    static void Copy(uint32_t        hitcnt,
                     uint32_t       *sortIndex_dst,
                     char           *sortData_dst,
                     const uint32_t *sortIndex_src,
                     const char     *sortData_src);
};


class SortDataIterator
{
private:
    const uint32_t *_ofs;
    const uint32_t *_ofs_end;
    const char     *_data;
    const char     *_buf;
    uint32_t        _len;

public:
    SortDataIterator()
        : _ofs(nullptr), _ofs_end(nullptr), _data(nullptr),
          _buf(nullptr), _len(0) {}

    void Next()
    {
        if (_ofs >= _ofs_end) {
            _buf = nullptr;
            _len = 0;
            return;
        }
        uint32_t tmp = *_ofs++;
        _buf = _data + tmp;
        // NB: *_ofs_end is a valid index entry
        _len = *_ofs - tmp;
    }

    void Init(uint32_t cnt,
              const uint32_t *idx,
              const char *data)
    {
        _ofs     = idx;
        _ofs_end = idx + cnt;
        _data    = data;
        _buf     = nullptr;
        _len     = 0;
        Next();
    }

    uint32_t GetLen() const { return _len; }
    const char *GetBuf() const { return _buf; }
    bool Before(SortDataIterator *other, bool beforeOnMatch = false);
};

}
