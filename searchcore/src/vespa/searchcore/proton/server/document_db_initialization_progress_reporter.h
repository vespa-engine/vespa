// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include <shared_mutex>
#include <vespa/vespalib/data/slime/inserter.h>

namespace proton {

class DocumentDB;

class DocumentDBInitializationProgressReporter : public initializer::IInitializationProgressReporter {
private:
    mutable std::shared_mutex _mutex;
    std::vector<IInitializationProgressReporter::SP> _attributes;

    std::string _name;
    DocumentDB &_documentDB;

public:
    DocumentDBInitializationProgressReporter(const std::string &name, DocumentDB &documentDB);
    void reportProgress(const vespalib::slime::Inserter &inserter) const override;
    void registerSubReporter(const IInitializationProgressReporter::SP &reporter) override;
};

} // namespace proton
