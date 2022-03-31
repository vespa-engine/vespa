// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketprocessor.h"
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>
#include <stdexcept>

namespace storage {

namespace {

class IteratorGuard
{
private:
    spi::PersistenceProvider& _spi;
    spi::IteratorId _iteratorId;

public:
    IteratorGuard(spi::PersistenceProvider& spi, spi::IteratorId iteratorId)
        : _spi(spi),
          _iteratorId(iteratorId)
    { }
    ~IteratorGuard()
    {
        assert(_iteratorId != 0);
        _spi.destroyIterator(_iteratorId);
    }
    spi::IteratorId getIteratorId() const { return _iteratorId; }
    spi::PersistenceProvider& getPersistenceProvider() const { return _spi; }
};

}

void
BucketProcessor::iterateAll(spi::PersistenceProvider& provider,
                            const spi::Bucket& bucket,
                            const std::string& documentSelection,
                            std::shared_ptr<document::FieldSet> field_set,
                            EntryProcessor& processor,
                            spi::IncludedVersions versions,
                            spi::Context& context)
{
    spi::Selection sel = spi::Selection(spi::DocumentSelection(documentSelection));
    spi::CreateIteratorResult createIterResult(provider.createIterator(
            bucket,
            std::move(field_set),
            sel,
            versions,
            context));

    if (createIterResult.getErrorCode() != spi::Result::ErrorType::NONE) {
        vespalib::asciistream ss;
        ss << "Failed to create iterator: "
           << createIterResult.getErrorMessage();
        throw std::runtime_error(ss.str());
    }

    spi::IteratorId iteratorId(createIterResult.getIteratorId());
    IteratorGuard iteratorGuard(provider, iteratorId);

    while (true) {
        spi::IterateResult result(provider.iterate(iteratorId, UINT64_MAX));
        if (result.getErrorCode() != spi::Result::ErrorType::NONE) {
            vespalib::asciistream ss;
            ss << "Failed: " << result.getErrorMessage();
            throw std::runtime_error(ss.str());
        }

        for (size_t i = 0; i < result.getEntries().size(); ++i) {
            processor.process(*result.getEntries()[i]);
        }

        if (result.isCompleted()) {
            break;
        }
    }
}

}
