// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "schemaconfigurer.h"
#include <vespa/searchcommon/config/subscriptionproxyng.h>

#include <vespa/log/log.h>
LOG_SETUP(".index.schemaconfigurer");

using namespace config;
using namespace vespa::config::search;

namespace search {
namespace index {


Schema::DataType
SchemaBuilder::convert(const IndexschemaConfig::Indexfield::Datatype &type)
{
    switch (type) {
    case IndexschemaConfig::Indexfield::STRING:
        return schema::STRING;
    case IndexschemaConfig::Indexfield::INT64:
        return schema::INT64;
    case IndexschemaConfig::Indexfield::BOOLEANTREE:
        return schema::BOOLEANTREE;
    }
    return schema::STRING;
}


Schema::CollectionType
SchemaBuilder::convert(const IndexschemaConfig::Indexfield::Collectiontype & type)
{
    switch (type) {
    case IndexschemaConfig::Indexfield::SINGLE:
        return schema::SINGLE;
    case IndexschemaConfig::Indexfield::ARRAY:
        return schema::ARRAY;
    case IndexschemaConfig::Indexfield::WEIGHTEDSET:
        return schema::WEIGHTEDSET;
    }
    return schema::SINGLE;
}


Schema::DataType
SchemaBuilder::convert(const AttributesConfig::Attribute::Datatype &type)
{
    switch (type) {
    case AttributesConfig::Attribute::STRING:
        return schema::STRING;
    case AttributesConfig::Attribute::UINT1:
        return schema::UINT1;
    case AttributesConfig::Attribute::UINT2:
        return schema::UINT2;
    case AttributesConfig::Attribute::UINT4:
        return schema::UINT4;
    case AttributesConfig::Attribute::INT8:
        return schema::INT8;
    case AttributesConfig::Attribute::INT16:
        return schema::INT16;
    case AttributesConfig::Attribute::INT32:
        return schema::INT32;
    case AttributesConfig::Attribute::INT64:
        return schema::INT64;
    case AttributesConfig::Attribute::FLOAT:
        return schema::FLOAT;
    case AttributesConfig::Attribute::DOUBLE:
        return schema::DOUBLE;
    case AttributesConfig::Attribute::PREDICATE:
        return schema::BOOLEANTREE;
    case AttributesConfig::Attribute::TENSOR:
        return schema::TENSOR;
    case AttributesConfig::Attribute::REFERENCE:
        return schema::REFERENCE;
    default:
        break;
    }
    // TODO: exception?
    return schema::STRING;
}


Schema::CollectionType
SchemaBuilder::convert(const AttributesConfig::Attribute::Collectiontype &type)
{
    switch (type) {
    case AttributesConfig::Attribute::SINGLE:
        return schema::SINGLE;
    case AttributesConfig::Attribute::ARRAY:
        return schema::ARRAY;
    case AttributesConfig::Attribute::WEIGHTEDSET:
        return schema::WEIGHTEDSET;
    }
    return schema::SINGLE;
}


Schema::DataType
SchemaBuilder::convertSummaryType(const vespalib::string & type)
{
    if (type == "byte") {
        return schema::INT8;
    } else if (type == "short") {
        return schema::INT16;
    } else if (type == "integer") {
        return schema::INT32;
    } else if (type == "int64") {
        return schema::INT64;
    } else if (type == "float") {
        return schema::FLOAT;
    } else if (type == "double") {
        return schema::DOUBLE;
    } else if (type == "string" ||
               type == "longstring" ||
               type == "xmlstring" ||
               type == "featuredata" ||
               type == "jsonstring")
    {
        return schema::STRING;
    } else if (type == "data" ||
               type == "longdata")
    {
        return schema::RAW;
    }
    return schema::RAW;
}


void
SchemaBuilder::build(const IndexschemaConfig &cfg, Schema &schema)
{
    for (size_t i = 0; i < cfg.indexfield.size(); ++i) {
        const IndexschemaConfig::Indexfield & f = cfg.indexfield[i];
        if ((f.datatype == IndexschemaConfig::Indexfield::BOOLEANTREE &&
            f.collectiontype == IndexschemaConfig::Indexfield::SINGLE) ||
            (f.indextype == IndexschemaConfig::Indexfield::RISE))
        {
            LOG(warning, "Your field '%s' is a rise index. Those are no longer supported as of Vespa-5.89.\n"
                         " Redeploy and follow instructions to mitigate.", f.name.c_str());
        } else {
            schema.addIndexField(Schema::IndexField(f.name, convert(f.datatype),
                                                convert(f.collectiontype)).
                                 setPrefix(f.prefix).
                                 setPhrases(f.phrases).
                                 setPositions(f.positions).
                                 setAvgElemLen(f.averageelementlen));
        }
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
    for (size_t i = 0; i < cfg.attribute.size(); ++i) {
        const AttributesConfig::Attribute & a = cfg.attribute[i];
        schema.addAttributeField(Schema::Field(a.name,
                                         convert(a.datatype),
                                         convert(a.collectiontype)));
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
SchemaBuilder::build(const ImportedFieldsConfig &cfg, Schema &schema)
{
    for (const auto &attr : cfg.attribute) {
        // TODO: Use correct datatype and collection type when available in config.
        schema.addImportedAttributeField(Schema::ImportedAttributeField(attr.name,
                                                                        schema::DataType::STRING));
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

void
SchemaConfigurer::configure(const vespa::config::search::ImportedFieldsConfig &cfg)
{
    SchemaBuilder::build(cfg, _schema);
}

SchemaConfigurer::SchemaConfigurer(Schema &schema,
                                   const vespalib::string &configId)
    : _schema(schema)
{
    search::SubscriptionProxyNg<SchemaConfigurer, IndexschemaConfig>
        indexSchemaSubscriber(*this, &SchemaConfigurer::configure);
    search::SubscriptionProxyNg<SchemaConfigurer, AttributesConfig>
        attributesSubscriber(*this, &SchemaConfigurer::configure);
    search::SubscriptionProxyNg<SchemaConfigurer, SummaryConfig>
        summarySubscriber(*this, &SchemaConfigurer::configure);
    search::SubscriptionProxyNg<SchemaConfigurer, ImportedFieldsConfig>
            importedFieldsSubscriber(*this, &SchemaConfigurer::configure);
    indexSchemaSubscriber.subscribe(configId.c_str());
    attributesSubscriber.subscribe(configId.c_str());
    summarySubscriber.subscribe(configId.c_str());
    importedFieldsSubscriber.subscribe(configId.c_str());
}


} // namespace search::index
} // namespace search
