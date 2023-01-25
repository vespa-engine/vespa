// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchsummary/docsummary/i_query_term_filter_factory.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vsm/config/config-vsmfields.h>
#include <vespa/vsm/config/config-vsmsummary.h>

namespace vsm {

/*
 * Class for creating an instance of IQueryTermFilter for streaming search.
 *
 * vsm summary fields are treated as document fields by the summary framework
 * in the searchsummary module, cf. IDocsumStoreDocument.
 */
class QueryTermFilterFactory : public search::docsummary::IQueryTermFilterFactory
{
public:
    using VsmfieldsConfig = vespa::config::search::vsm::VsmfieldsConfig;
    using VsmsummaryConfig = vespa::config::search::vsm::VsmsummaryConfig;
private:
    using StringSet = vespalib::hash_set<vespalib::string>;
    using StringSetMap = vespalib::hash_map<vespalib::string, StringSet>;
    StringSetMap _view_map;  // document field    -> views
    StringSetMap _field_map; // vsm summary field -> document fields
    void populate_view_map(VsmfieldsConfig& vsm_fields_config);
    void populate_field_map(VsmsummaryConfig& vsm_summary_config);
    void populate_views(StringSet& views, const vespalib::string& field) const;
public:
    QueryTermFilterFactory(VsmfieldsConfig& vsm_fields_config,
                            VsmsummaryConfig& vsm_summary_config);
    ~QueryTermFilterFactory() override;
    std::shared_ptr<const search::docsummary::IQueryTermFilter> make(vespalib::stringref input_field) const override;
};

}
