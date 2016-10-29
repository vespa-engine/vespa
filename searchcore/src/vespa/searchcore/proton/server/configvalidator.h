// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/schema.h>
#include <vespa/config-attributes.h>

namespace proton {

/**
 * Class used to validate new document db config before starting using it.
 **/
class ConfigValidator {
public:
    /**
     * The various results of a schema check.
     * All but OK means that the new schema should be rejected.
     */
    enum ResultType
    {
        OK,
        DATA_TYPE_CHANGED,
        COLLECTION_TYPE_CHANGED,
        INDEX_ASPECT_ADDED,
        INDEX_ASPECT_REMOVED,
        ATTRIBUTE_ASPECT_ADDED,
        ATTRIBUTE_ASPECT_REMOVED,
        ATTRIBUTE_FAST_ACCESS_ADDED,
        ATTRIBUTE_FAST_ACCESS_REMOVED,
        ATTRIBUTE_TENSOR_TYPE_CHANGED
    };

    class Result
    {
    private:
        ResultType _type;
        vespalib::string _what;
    public:
        Result()
            : _type(OK),
              _what("")
        {}
        Result(ResultType type_, const vespalib::string &what_)
            : _type(type_),
              _what(what_)
        {}
        ResultType type() const { return _type; }
        const vespalib::string &what() const { return _what; }
        bool ok() const { return type() == OK; }
    };

    class Config
    {
    private:
        const search::index::Schema &_schema;
        const vespa::config::search::AttributesConfig &_attributeCfg;
    public:
        Config(const search::index::Schema &schema,
               const vespa::config::search::AttributesConfig &attributeCfg)
            : _schema(schema),
              _attributeCfg(attributeCfg)
        {}
        const search::index::Schema &getSchema() const {
            return _schema;
        }
        const vespa::config::search::AttributesConfig &getAttributeConfig() const {
            return _attributeCfg;
        }
    };

    /**
     * Check if new schema can be applied or not.
     */
    static Result
    validate(const Config &newCfg,
             const Config &oldCfg,
             const search::index::Schema &oldHistory);
};

} // namespace proton

