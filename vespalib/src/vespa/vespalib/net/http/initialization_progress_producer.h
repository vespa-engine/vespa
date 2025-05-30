// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/slime/inserter.h>

namespace vespalib {

    struct InitializationProgressProducer {
        virtual ~InitializationProgressProducer() = default;
        virtual void getProgress(const vespalib::slime::Inserter &inserter) const = 0;
    };

} // namespace vespalib
