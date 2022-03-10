// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "i_enum_store.h"
#include "enum_store_loaders.h"

namespace search {

enumstore::EnumeratedLoader
IEnumStore::make_enumerated_loader() {
    return enumstore::EnumeratedLoader(*this);
}

enumstore::EnumeratedPostingsLoader
IEnumStore::make_enumerated_postings_loader() {
    return enumstore::EnumeratedPostingsLoader(*this);
}

}
