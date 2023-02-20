// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_reconfig.h"
#include "document_subdb_reconfig.h"

namespace proton {

DocumentDBReconfig::DocumentDBReconfig(vespalib::steady_time start_time,
                                       std::unique_ptr<DocumentSubDBReconfig> ready_reconfig_in,
                                       std::unique_ptr<DocumentSubDBReconfig> not_ready_reconfig_in)
    : _start_time(start_time),
      _ready_reconfig(std::move(ready_reconfig_in)),
      _not_ready_reconfig(std::move(not_ready_reconfig_in))
{
}

DocumentDBReconfig::~DocumentDBReconfig() = default;

}

