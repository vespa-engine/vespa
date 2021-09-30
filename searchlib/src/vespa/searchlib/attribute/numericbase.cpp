// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "numericbase.hpp"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.numericbase");

namespace search {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(NumericAttribute, AttributeVector);

void
NumericAttribute::load_enumerated_data(ReaderBase&,
                                       enumstore::EnumeratedPostingsLoader&,
                                       size_t)
{
    LOG_ABORT("Should not be reached");
}

void
NumericAttribute::load_enumerated_data(ReaderBase&,
                                       enumstore::EnumeratedLoader&)
{
    LOG_ABORT("Should not be reached");
}

void
NumericAttribute::load_posting_lists_and_update_enum_store(enumstore::EnumeratedPostingsLoader&)
{
    LOG_ABORT("Should not be reached");
}

template class NumericAttribute::Range<int8_t>;
template class NumericAttribute::Range<int16_t>;
template class NumericAttribute::Range<int32_t>;
template class NumericAttribute::Range<int64_t>;
template class NumericAttribute::Range<float>;
template class NumericAttribute::Range<double>;

template class NumericAttribute::Equal<int8_t>;
template class NumericAttribute::Equal<int16_t>;
template class NumericAttribute::Equal<int32_t>;
template class NumericAttribute::Equal<int64_t>;
template class NumericAttribute::Equal<float>;
template class NumericAttribute::Equal<double>;

}
