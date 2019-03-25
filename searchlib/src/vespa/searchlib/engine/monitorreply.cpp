// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "monitorreply.h"

namespace search::engine {

MonitorReply::MonitorReply()
    : mld(),
      activeDocsRequested(false),
      partid(),
      distribution_key(-1),
      timestamp(),
      totalNodes(),
      activeNodes(),
      totalParts(),
      activeParts(),
      activeDocs(0),
      flags()
{ }

}

