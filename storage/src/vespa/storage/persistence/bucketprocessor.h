// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Class that simplifies operations where we want to iterate through all
 * the documents in a bucket (possibly with a document selection) and do
 * something with each entry.
 */
#pragma once

#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/persistence/spi/context.h>

namespace document { class FieldSet; }
namespace storage::spi { struct PersistenceProvider; }

namespace storage {

class BucketProcessor
{
public:
    class EntryProcessor {
    public:
        virtual ~EntryProcessor() {};
        virtual void process(spi::DocEntry&) = 0;
    };

    static void iterateAll(spi::PersistenceProvider&,
                           const spi::Bucket&,
                           const std::string& documentSelection,
                           std::shared_ptr<document::FieldSet> field_set,
                           EntryProcessor&,
                           spi::IncludedVersions,
                           spi::Context&);
};

}

