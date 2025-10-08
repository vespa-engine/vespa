// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <memory>

namespace search { class SerializedQueryTree; }

namespace search::docsummary {

class GetDocsumArgs
{
private:
    using FieldSet = vespalib::hash_set<std::string>;
    std::string                      _resultClassName;
    bool                             _dumpFeatures;
    bool                             _locations_possible;
    SerializedQueryTreeSP            _serializedQueryTree;
    std::string                      _location;
    vespalib::duration               _timeout;
    fef::Properties                  _highlightTerms;
    FieldSet                         _fields;
public:
    GetDocsumArgs();
    GetDocsumArgs(const GetDocsumArgs &) = delete;
    GetDocsumArgs & operator=(const GetDocsumArgs &) = delete;
    ~GetDocsumArgs();

    void initFromDocsumRequest(const search::engine::DocsumRequest &req);

    void setResultClassName(std::string_view name) { _resultClassName = name; }
    void setSerializedQueryTree(SerializedQueryTreeSP tree) { _serializedQueryTree = std::move(tree); }
    void locations_possible(bool value) { _locations_possible = value; }
    bool locations_possible() const { return _locations_possible; }
    const std::string &getLocation() const { return _location; }
    void setLocation(const std::string & location) { _location = location; }
    void setTimeout(vespalib::duration timeout) { _timeout = timeout; }
    vespalib::duration getTimeout() const { return _timeout; }

    const std::string & getResultClassName()      const { return _resultClassName; }
    const search::SerializedQueryTree& getSerializedQueryTree() const {
        return _serializedQueryTree ? *_serializedQueryTree : search::SerializedQueryTree::empty();
    }

    void dumpFeatures(bool v) { _dumpFeatures = v; }
    bool dumpFeatures() const { return _dumpFeatures; }

    const fef::Properties &highlightTerms() const { return _highlightTerms; }
    void highlightTerms(fef::Properties & terms) { _highlightTerms = terms; }
    void set_fields(const FieldSet& fields_in) { _fields = fields_in; }
    const FieldSet& get_fields() const { return _fields; }
    bool need_field(std::string_view field) const;
};

}
