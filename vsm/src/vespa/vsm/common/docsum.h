// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vsm/common/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>

namespace vsm
{

/**
  Will represent a cache of the document summaries. -> Actual docsums will be
  generated on the fly when requested. A document summary is accessed by its
  documentId.
*/

class IDocSumCache
{
public:
  virtual const Document & getDocSum(const search::DocumentIdT & docId) const = 0;
  virtual ~IDocSumCache() { }
};

class DocSumCache : public search::Object, public IDocSumCache
{
 public:
  typedef vespalib::hash_map<search::DocumentIdT, Document::SP> DocSumCacheT;
  DUPLICATE(DocSumCache);
  DocSumCache();
  virtual ~DocSumCache();
  virtual const Document & getDocSum(const search::DocumentIdT & docId) const;
  void push_back(const Document::SP & doc);
  void insert(const DocSumCache & dc);
  const DocSumCacheT & cache()  const { return _list; }
 private:
  DocSumCacheT _list;
};

}

