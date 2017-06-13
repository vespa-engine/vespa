// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-summary.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcommon/attribute/collectiontype.h>
#include <vespa/searchcommon/attribute/basictype.h>

namespace search {
namespace index {

/**
 * Schema class used to give a high-level description of the content
 * of an index.
 **/
class SchemaBuilder
{
public:
    /**
     * Build from indexschema config.
     *
     * @param indexCfg vespa::config::search::IndexschemaConfig to use
     */
    static void build(const vespa::config::search::IndexschemaConfig &cfg, Schema &schema);
    /**
     * Build from attribute config.
     *
     * @param attributeCfg vespa::config::search::AttributesConfig to use
     **/
    static void build(const vespa::config::search::AttributesConfig &cfg, Schema &schema);
    /**
     * Build from summary config.
     *
     * @param summaryCfg vespa::config::search::SummaryConfig to use
     **/
    static void build(const vespa::config::search::SummaryConfig &cfg, Schema &schema);

    static void build(const vespa::config::search::ImportedFieldsConfig &cfg, Schema &schema);
};

class SchemaConfigurer
{
private:
    Schema & _schema;
    void configure(const vespa::config::search::IndexschemaConfig & cfg);
    void configure(const vespa::config::search::AttributesConfig & cfg);
    void configure(const vespa::config::search::SummaryConfig & cfg);
    void configure(const vespa::config::search::ImportedFieldsConfig &cfg);

public:
    /**
     * Load this schema from config using the given config id.
     *
     * @param configId the config id used to retrieve the relevant config.
     **/
    SchemaConfigurer(Schema & schema, const vespalib::string &configId);
};

} // namespace search::index
} // namespace search

