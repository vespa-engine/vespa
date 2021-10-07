// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespa::config::search::internal {
    class InternalIndexschemaType;
    class InternalAttributesType;
    class InternalSummaryType;
}

namespace search::index {

class Schema;

/**
 * Schema class used to give a high-level description of the content
 * of an index.
 **/
class SchemaBuilder
{
public:
    using IndexschemaConfig = const vespa::config::search::internal::InternalIndexschemaType;
    using AttributesConfig = const vespa::config::search::internal::InternalAttributesType;
    using SummaryConfig = const vespa::config::search::internal::InternalSummaryType;
    /**
     * Build from indexschema config.
     *
     * @param indexCfg IndexschemaConfig to use
     */
    static void build(const IndexschemaConfig &cfg, Schema &schema);
    /**
     * Build from attribute config.
     *
     * @param attributeCfg AttributesConfig to use
     **/
    static void build(const AttributesConfig &cfg, Schema &schema);
    /**
     * Build from summary config.
     *
     * @param summaryCfg SummaryConfig to use
     **/
    static void build(const SummaryConfig &cfg, Schema &schema);

};

class SchemaConfigurer
{
private:
    using IndexschemaConfig = SchemaBuilder::IndexschemaConfig;
    using AttributesConfig = SchemaBuilder::AttributesConfig;
    using SummaryConfig = SchemaBuilder::SummaryConfig;
    Schema & _schema;
    void configure(const IndexschemaConfig & cfg);
    void configure(const AttributesConfig & cfg);
    void configure(const SummaryConfig & cfg);

public:
    /**
     * Load this schema from config using the given config id.
     *
     * @param configId the config id used to retrieve the relevant config.
     **/
    SchemaConfigurer(Schema & schema, const vespalib::string &configId);
};

}
