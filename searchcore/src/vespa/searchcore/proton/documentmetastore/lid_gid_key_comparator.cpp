// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_gid_key_comparator.h"

namespace proton::documentmetastore {

LidGidKeyComparator::LidGidKeyComparator(const document::GlobalId &gid,
                                         const MetaDataStore &metaDataStore)
    : _gid(gid),
      _metaDataView(&metaDataStore.acquire_elem_ref(0)),
      _gidCompare()
{
}

LidGidKeyComparator::LidGidKeyComparator(const RawDocumentMetaData &metaData,
                                         const MetaDataStore &metaDataStore)
    : _gid(metaData.getGid()),
      _metaDataView(&metaDataStore.acquire_elem_ref(0)),
      _gidCompare()
{
}

}
