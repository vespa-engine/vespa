// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "monitorreply.h"

namespace search::engine {

MonitorReply::MonitorReply()
    : activeDocs(0),
      targetActiveDocs(0),
      distribution_key(-1),
      timestamp(),
      is_blocking_writes(false)
{ }

}

