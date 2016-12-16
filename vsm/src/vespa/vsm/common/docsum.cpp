// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsum.h"
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#define DEBUGMASK 0x00

using search::DocumentIdT;

namespace vsm {

using document::FieldValue;
using document::StringFieldValue;

IMPLEMENT_DUPLICATE(DocSumCache);

DocSumCache::DocSumCache() :
  _list()
{ }

DocSumCache::~DocSumCache() { }

const Document & DocSumCache::getDocSum(const DocumentIdT & docId) const
{
    DocSumCacheT::const_iterator found = _list.find(docId);
    return *found->second;
}

void DocSumCache::push_back(const Document::SP & docSum)
{
  _list[docSum->getDocId()] = docSum;
}

void DocSumCache::insert(const DocSumCache & dc)
{
  for (DocSumCacheT::const_iterator itr = dc._list.begin(); itr != dc._list.end(); ++itr) {
    if (_list.find(itr->first) == _list.end()) {
      _list[itr->first] = itr->second;
    }
  }
}

}
