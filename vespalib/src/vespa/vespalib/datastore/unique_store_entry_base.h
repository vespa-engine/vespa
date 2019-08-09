// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstring>

namespace search::datastore {

/*
 * class containing common metadata for entries in unique store.
 */
class UniqueStoreEntryBase {
protected:
    UniqueStoreEntryBase() {}
    ~UniqueStoreEntryBase() = default;
};

}
