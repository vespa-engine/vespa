// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_gid_key_comparator.h"

namespace proton::documentmetastore {

LidGidKeyComparator::LidGidKeyComparator(const document::GlobalId &gid,
                                         UnboundMetaDataView metaDataView)
    : _gid(gid),
      _metaDataView(metaDataView),
      _gidCompare()
{
}

LidGidKeyComparator::LidGidKeyComparator(const RawDocumentMetaData &metaData,
                                         UnboundMetaDataView metaDataView)
    : _gid(metaData.getGid()),
      _metaDataView(metaDataView),
      _gidCompare()
{
}

}
