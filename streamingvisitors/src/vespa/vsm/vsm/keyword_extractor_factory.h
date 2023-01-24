// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchsummary/docsummary/i_keyword_extractor_factory.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vsm/config/config-vsmfields.h>
#include <vespa/vsm/config/config-vsmsummary.h>

namespace vsm {

/*
 * Class for creating an instance of IKeywordExtractor for streaming search.
 *
 * vsm summary fields are treated as document fields by the summary framework
 * in the searchsummary module, cf. IDocsumStoreDocument.
 */
class KeywordExtractorFactory : public search::docsummary::IKeywordExtractorFactory
{
public:
    using VsmfieldsConfig = vespa::config::search::vsm::VsmfieldsConfig;
    using VsmsummaryConfig = vespa::config::search::vsm::VsmsummaryConfig;
private:
    using StringSet = vespalib::hash_set<vespalib::string>;
    using StringSetMap = vespalib::hash_map<vespalib::string, StringSet>;
    StringSetMap _index_map; // document field    -> indexes
    StringSetMap _field_map; // vsm summary field -> document fields
    void populate_index_map(VsmfieldsConfig& vsm_fields_config);
    void populate_field_map(VsmsummaryConfig& vsm_summary_config);
    void populate_indexes(StringSet& indexes, const vespalib::string& field) const;
public:
    KeywordExtractorFactory(VsmfieldsConfig& vsm_fields_config,
                            VsmsummaryConfig& vsm_summary_config);
    ~KeywordExtractorFactory() override;
    std::shared_ptr<const search::docsummary::IKeywordExtractor> make(vespalib::stringref input_field) const override;
};

}
