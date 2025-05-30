// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include <shared_mutex>

namespace proton {

class AttributeInitializationProgressReporter : public initializer::IInitializationProgressReporter {
public:
    using SP = std::shared_ptr<AttributeInitializationProgressReporter>;
    explicit AttributeInitializationProgressReporter(const std::string &name);
    void setAttributeVector(const search::AttributeVector::SP &attr);

    void reportProgress(const vespalib::slime::Inserter &inserter) const override;

private:
    mutable std::shared_mutex _mutex;

    const std::string _name;
    search::AttributeVector::SP _attr;
};

} // namespace proton
