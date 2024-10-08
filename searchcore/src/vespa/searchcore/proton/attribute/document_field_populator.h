// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    std::string  _fieldName;
    AttributeVectorSP _attr;
    std::string  _subDbName;
    int64_t           _documentsPopulated;

public:
    DocumentFieldPopulator(const std::string &fieldName,
                           AttributeVectorSP attr,
                           const std::string &subDbName);

    ~DocumentFieldPopulator() override;

    const search::AttributeVector &getAttribute() const {
        return *_attr;
    }

    void handleExisting(uint32_t lid, const std::shared_ptr<document::Document> &doc) override;
};

} // namespace proton

