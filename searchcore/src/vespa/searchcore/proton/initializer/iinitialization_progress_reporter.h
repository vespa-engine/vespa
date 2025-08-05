// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace vespalib::slime {
    struct Inserter;
}

namespace proton::initializer {

class IInitializationProgressReporter {
public:
    using SP = std::shared_ptr<IInitializationProgressReporter>;
    virtual ~IInitializationProgressReporter() = default;

    virtual void reportProgress(const vespalib::slime::Inserter &) const = 0;
};

} // namespace proton::initializer
