// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fieldsearcher.h"

namespace vsm {

class IntFieldSearcher : public FieldSearcher
{
public:
    std::unique_ptr<FieldSearcher> duplicate() const override;
    IntFieldSearcher(FieldIdT fId=0);
    ~IntFieldSearcher();
    void prepare(search::streaming::QueryTermList& qtl,
                 const SharedSearcherBuf& buf,
                 const vsm::FieldPathMapT& field_paths,
                 search::fef::IQueryEnvironment& query_env) override;
    void onValue(const document::FieldValue & fv) override;
protected:
    class IntInfo
    {
    public:
        IntInfo(int64_t low, int64_t high, bool v) : _lower(low), _upper(high), _valid(v) { if (low > high) { _lower = high; _upper = low; } }
        bool cmp(int64_t key) const { return (_lower <= key) && (key <= _upper); }
        bool valid()          const { return _valid; }
    private:
        int64_t _lower;
        int64_t _upper;
        bool    _valid;
    };
    using IntInfoListT = std::vector<IntInfo>;
    IntInfoListT _intTerm;
};

}

