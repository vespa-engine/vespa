// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "schemaconfigurer.h"
#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-summary.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcommon/attribute/collectiontype.h>
#include <vespa/searchcommon/attribute/basictype.h>

#include <vespa/searchcommon/config/subscriptionproxyng.h>

#include <vespa/log/log.h>
LOG_SETUP(".index.schemaconfigurer");

using namespace config;
using namespace vespa::config::search;

namespace search::index {

using schema::DataType;
using schema::CollectionType;

namespace {

Schema::DataType
convertIndexDataType(const IndexschemaConfig::Indexfield::Datatype &type)
{
    switch (type) {
    case IndexschemaConfig::Indexfield::Datatype::STRING:
        return DataType::STRING;
    case IndexschemaConfig::Indexfield::Datatype::INT64:
        return DataType::INT64;
    }
    return DataType::STRING;
}


Schema::CollectionType
convertIndexCollectionType(const IndexschemaConfig::Indexfield::Collectiontype &type)
{
    switch (type) {
    case IndexschemaConfig::Indexfield::Collectiontype::SINGLE:
        return CollectionType::SINGLE;
    case IndexschemaConfig::Indexfield::Collectiontype::ARRAY:
        return CollectionType::ARRAY;
    case IndexschemaConfig::Indexfield::Collectiontype::WEIGHTEDSET:
        return CollectionType::WEIGHTEDSET;
    }
    return CollectionType::SINGLE;
}

template <typename ConfigType>
Schema::DataType
convertDataType(const ConfigType &type)
{
    switch (type) {
    case ConfigType::STRING:
        return DataType::STRING;
    case ConfigType::BOOL:
        return DataType::BOOL;
    case ConfigType::UINT2:
        return DataType::UINT2;
    case ConfigType::UINT4:
        return DataType::UINT4;
    case ConfigType::INT8:
        return DataType::INT8;
    case ConfigType::INT16:
        return DataType::INT16;
    case ConfigType::INT32:
        return DataType::INT32;
    case ConfigType::INT64:
        return DataType::INT64;
    case ConfigType::FLOAT:
        return DataType::FLOAT;
    case ConfigType::DOUBLE:
        return DataType::DOUBLE;
    case ConfigType::PREDICATE:
        return DataType::BOOLEANTREE;
    case ConfigType::TENSOR:
        return DataType::TENSOR;
    case ConfigType::REFERENCE:
        return DataType::REFERENCE;
    default:
        break;
    }
    // TODO: exception?
    return DataType::STRING;
}

template <typename ConfigType>
Schema::CollectionType
convertCollectionType(const ConfigType &type)
{
    switch (type) {
    case ConfigType::SINGLE:
        return CollectionType::SINGLE;
    case ConfigType::ARRAY:
        return CollectionType::ARRAY;
    case ConfigType::WEIGHTEDSET:
        return CollectionType::WEIGHTEDSET;
    }
    return CollectionType::SINGLE;
}


Schema::DataType
convertSummaryType(const vespalib::string &type)
{
    if (type == "byte") {
        return DataType::INT8;
    } else if (type == "short") {
        return DataType::INT16;
    } else if (type == "integer") {
        return DataType::INT32;
    } else if (type == "int64") {
        return DataType::INT64;
    } else if (type == "float") {
        return DataType::FLOAT;
    } else if (type == "double") {
        return DataType::DOUBLE;
    } else if (type == "string" ||
               type == "longstring" ||
               type == "xmlstring" ||
               type == "featuredata" ||
               type == "jsonstring")
    {
        return DataType::STRING;
    } else if (type == "data" ||
               type == "longdata")
    {
        return DataType::RAW;
    }
    return DataType::RAW;
}

}

void
SchemaBuilder::build(const IndexschemaConfig &cfg, Schema &schema)
{
    for (size_t i = 0; i < cfg.indexfield.size(); ++i) {
        const IndexschemaConfig::Indexfield & f = cfg.indexfield[i];
        schema.addIndexField(Schema::IndexField(f.name, convertIndexDataType(f.datatype),
                                                convertIndexCollectionType(f.collectiontype)).
                setAvgElemLen(f.averageelementlen).
                set_interleaved_features(f.interleavedfeatures));
    }
    for (size_t i = 0; i < cfg.fieldset.size(); ++i) {
        const IndexschemaConfig::Fieldset &fs = cfg.fieldset[i];
        Schema::FieldSet toAdd(fs.name);
        for (size_t j = 0; j < fs.field.size(); ++j) {
            toAdd.addField(fs.field[j].name);
        }
        schema.addFieldSet(toAdd);
    }
}


void
SchemaBuilder::build(const AttributesConfig &cfg, Schema &schema)
{
    for (const auto &attr : cfg.attribute) {
        if (attr.imported) {
            schema.addImportedAttributeField(Schema::ImportedAttributeField(attr.name,
                                                                            convertDataType(attr.datatype),
                                                                            convertCollectionType(attr.collectiontype)));
        } else {
            schema.addAttributeField(Schema::Field(attr.name,
                                                   convertDataType(attr.datatype),
                                                   convertCollectionType(attr.collectiontype)));
        }
    }
}


void
SchemaBuilder::build(const SummaryConfig &cfg, Schema &schema)
{
    for (size_t i = 0; i < cfg.classes.size(); ++i) {
        LOG(debug, "class with index %lu has id %d (default has id %d)",
            i, cfg.classes[i].id, cfg.defaultsummaryid);
    }
    for (size_t i = 0; i < cfg.classes.size(); ++i) {
        // use the default summary class that has all fields
        if (cfg.classes[i].id == cfg.defaultsummaryid) {
            for (size_t j = 0; j < cfg.classes[i].fields.size(); ++j) {
                const SummaryConfig::Classes::Fields & f =
                    cfg.classes[i].fields[j];
                schema.addSummaryField(Schema::Field(f.name,
                                               convertSummaryType(f.type)));
            }
            return;
        }
    }
    if (cfg.classes.empty()) {
        LOG(debug,
            "No summary class configured that match the default summary id %d",
            cfg.defaultsummaryid);
    } else {
        LOG(warning,
            "No summary class configured that match the default summary id %d",
            cfg.defaultsummaryid);
    }
}

void
SchemaConfigurer::configure(const IndexschemaConfig &cfg)
{
    SchemaBuilder::build(cfg, _schema);
}

void
SchemaConfigurer::configure(const AttributesConfig &cfg)
{
    SchemaBuilder::build(cfg, _schema);
}

void
SchemaConfigurer::configure(const SummaryConfig & cfg)
{
    SchemaBuilder::build(cfg, _schema);
}

SchemaConfigurer::SchemaConfigurer(Schema &schema, const vespalib::string &configId)
    : _schema(schema)
{
    search::SubscriptionProxyNg<SchemaConfigurer, IndexschemaConfig>
        indexSchemaSubscriber(*this, &SchemaConfigurer::configure);
    search::SubscriptionProxyNg<SchemaConfigurer, AttributesConfig>
        attributesSubscriber(*this, &SchemaConfigurer::configure);
    search::SubscriptionProxyNg<SchemaConfigurer, SummaryConfig>
        summarySubscriber(*this, &SchemaConfigurer::configure);
    indexSchemaSubscriber.subscribe(configId.c_str());
    attributesSubscriber.subscribe(configId.c_str());
    summarySubscriber.subscribe(configId.c_str());
}

}
