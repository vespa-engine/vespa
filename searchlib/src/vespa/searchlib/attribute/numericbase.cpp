// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "numericbase.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.numericbase");

namespace search {

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

}
