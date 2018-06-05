// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/reprocessing/i_reprocessing_rewriter.h>
#include <vespa/searchlib/attribute/attributeguard.h>

namespace proton {

/**
 * Class used to populate a document field based on the content from an attribute vector.
 */
class DocumentFieldPopulator : public IReprocessingRewriter
{
private:
    using AttributeVectorSP = std::shared_ptr<search::AttributeVector>;
    vespalib::string _fieldName;
    AttributeVectorSP _attr;
    vespalib::string _subDbName;
    int64_t _documentsPopulated;

public:
    DocumentFieldPopulator(const vespalib::string &fieldName,
                           AttributeVectorSP attr,
                           const vespalib::string &subDbName);

    ~DocumentFieldPopulator();

    const search::AttributeVector &getAttribute() const {
        return *_attr;
    }

    // Implements IReprocessingRewriter
    virtual void handleExisting(uint32_t lid, const std::shared_ptr<document::Document> &doc) override;
};

} // namespace proton

