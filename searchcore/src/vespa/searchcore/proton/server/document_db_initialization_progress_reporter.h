// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/initializer/iinitialization_progress_reporter.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <shared_mutex>
#include <vector>

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
    std::vector<IInitializationProgressReporter::SP>& getAttributeProgressReporters() { return _attributes; };
};

} // namespace proton
