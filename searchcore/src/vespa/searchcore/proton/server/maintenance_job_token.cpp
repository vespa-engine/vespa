// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maintenance_job_token.h"
#include "maintenance_job_token_source.h"

namespace proton {

MaintenanceJobToken::MaintenanceJobToken(std::weak_ptr<MaintenanceJobTokenSource> source)
    : _source(std::move(source))
{
}

MaintenanceJobToken::~MaintenanceJobToken()
{
    auto source = _source.lock();
    if (source) {
        source->token_destroyed();
    }
}

}
