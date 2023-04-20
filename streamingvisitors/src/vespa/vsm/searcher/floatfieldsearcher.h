// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fieldsearcher.h"

namespace vsm {

template <typename T>
class FloatFieldSearcherT : public FieldSearcher
{
public:
    FloatFieldSearcherT(FieldIdT fId=0);
    ~FloatFieldSearcherT();
    void prepare(search::streaming::QueryTermList& qtl,
                 const SharedSearcherBuf& buf,
                 const vsm::FieldPathMapT& field_paths,
                 search::fef::IQueryEnvironment& query_env) override;
    void onValue(const document::FieldValue & fv) override;
protected:
    class FloatInfo
    {
    public:
        FloatInfo(T low, T high, bool v) : _lower(low), _upper(high), _valid(v) { if (low > high) { _lower = high; _upper = low; } }
        bool cmp(T key) const;
        bool valid()          const { return _valid; }
        void setValid(bool v)       { _valid = v; }
        T getLow()            const { return _lower; }
        T getHigh()           const { return _upper; }
    private:
        T _lower;
        T _upper;
        bool    _valid;
    };
    using FloatInfoListT = std::vector<FloatInfo>;
    FloatInfoListT _floatTerm;
};

using FloatFieldSearcherTF = FloatFieldSearcherT<float>;
using FloatFieldSearcherTD = FloatFieldSearcherT<double>;

class FloatFieldSearcher : public FloatFieldSearcherTF
{
public:
    std::unique_ptr<FieldSearcher> duplicate() const override;
    FloatFieldSearcher(FieldIdT fId=0) : FloatFieldSearcherTF(fId) { }
};

class DoubleFieldSearcher : public FloatFieldSearcherTD
{
public:
    std::unique_ptr<FieldSearcher> duplicate() const override;
    DoubleFieldSearcher(FieldIdT fId=0) : FloatFieldSearcherTD(fId) { }
};

}

