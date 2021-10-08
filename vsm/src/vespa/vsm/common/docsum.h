// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document.h"

namespace vsm {

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

}

