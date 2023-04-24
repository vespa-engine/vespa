// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "floatfieldsearcher.h"

using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

namespace vsm {

std::unique_ptr<FieldSearcher>
FloatFieldSearcher::duplicate() const
{
    return std::make_unique<FloatFieldSearcher>(*this);
}

std::unique_ptr<FieldSearcher>
DoubleFieldSearcher::duplicate() const
{
    return std::make_unique<DoubleFieldSearcher>(*this);
}

template<typename T>
FloatFieldSearcherT<T>::FloatFieldSearcherT(FieldIdT fId) :
    FieldSearcher(fId),
    _floatTerm()
{}

template<typename T>
FloatFieldSearcherT<T>::~FloatFieldSearcherT() {}

template<typename T>
void FloatFieldSearcherT<T>::prepare(search::streaming::QueryTermList& qtl,
                                     const SharedSearcherBuf& buf,
                                     const vsm::FieldPathMapT& field_paths,
                                     search::fef::IQueryEnvironment& query_env)
{
    _floatTerm.clear();
    FieldSearcher::prepare(qtl, buf, field_paths, query_env);
    for (auto qt : qtl) {
    size_t sz(qt->termLen());
        if (sz) {
            double low;
            double high;
            bool valid = qt->getAsDoubleTerm(low, high);
            _floatTerm.push_back(FloatInfo(low, high, valid));
        }
    }
}


template<typename T>
void FloatFieldSearcherT<T>::onValue(const document::FieldValue & fv)
{
    for(size_t j=0, jm(_floatTerm.size()); j < jm; j++) {
        const FloatInfo & ii = _floatTerm[j];
        if (ii.valid() && (ii.cmp(fv.getAsDouble()))) {
            addHit(*_qtl[j], 0);
        }
    }
    ++_words;
}

template<typename T>
bool FloatFieldSearcherT<T>::FloatInfo::cmp(T key) const
{
    return (_lower <= key) && (key <= _upper);
}

template class FloatFieldSearcherT<float>;
template class FloatFieldSearcherT<double>;

}
