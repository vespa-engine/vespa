// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "idatastore.h"

namespace search {

IDataStore::IDataStore(const vespalib::string& dirName) :
    _docIdLimit(0),
    _dirName(dirName)
{
}

IDataStore::~IDataStore()
{
}

} // namespace search
