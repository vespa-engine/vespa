// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/common/statusreport.h>
#include <vespa/vespalib/net/http/health_producer.h>

namespace proton {

class HealthAdapter : public vespalib::HealthProducer
{
private:
    const StatusProducer &_statusProducer;

public:
    HealthAdapter(const StatusProducer &sp);
    Health getHealth() const override;
};

} // namespace proton

