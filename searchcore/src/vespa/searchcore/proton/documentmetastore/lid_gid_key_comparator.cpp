// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_gid_key_comparator.h"

namespace proton::documentmetastore {

LidGidKeyComparator::LidGidKeyComparator(const document::GlobalId &gid,
                                         const MetaDataStore &metaDataStore)
    : _gid(gid),
      _metaDataStore(metaDataStore),
      _gidCompare()
{
}

LidGidKeyComparator::LidGidKeyComparator(const RawDocumentMetaData &metaData,
                                         const MetaDataStore &metaDataStore)
    : _gid(metaData.getGid()),
      _metaDataStore(metaDataStore),
      _gidCompare()
{
}

}
