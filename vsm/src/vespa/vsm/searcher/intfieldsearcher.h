// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vsm/searcher/fieldsearcher.h>

namespace vsm
{

class IntFieldSearcher : public FieldSearcher
{
public:
    DUPLICATE(IntFieldSearcher);
    IntFieldSearcher(FieldIdT fId=0);
    virtual void prepare(search::QueryTermList & qtl, const SharedSearcherBuf & buf) override;
    virtual void onValue(const document::FieldValue & fv) override;
protected:
    class IntInfo
    {
    public:
        IntInfo(int64_t low, int64_t high, bool v) : _lower(low), _upper(high), _valid(v) { if (low > high) { _lower = high; _upper = low; } }
        bool cmp(int64_t key) const { return (_lower <= key) && (key <= _upper); }
        bool valid()          const { return _valid; }
        void setValid(bool v)       { _valid = v; }
    private:
        int64_t _lower;
        int64_t _upper;
        bool    _valid;
    };
    typedef std::vector<IntInfo> IntInfoListT;
    IntInfoListT _intTerm;
};

}

