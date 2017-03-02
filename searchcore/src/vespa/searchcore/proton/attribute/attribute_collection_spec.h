// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/common/serialnum.h>
#include <memory>
#include <vector>

namespace proton {

/**
 * A specification of which attribute vectors an attribute manager should instantiate and manage.
 */
class AttributeCollectionSpec
{
public:
    typedef std::unique_ptr<AttributeCollectionSpec> UP;

    class Attribute
    {
    private:
        vespalib::string _name;
        search::attribute::Config _cfg;
    public:
        Attribute(const vespalib::string &name,
                  const search::attribute::Config &cfg);
        Attribute(const Attribute &);
        Attribute & operator=(const Attribute &);
        Attribute(Attribute &&);
        Attribute & operator=(Attribute &&);
        ~Attribute();
        const vespalib::string &getName() const { return _name; }
        const search::attribute::Config &getConfig() const { return _cfg; }
    };

    typedef std::vector<Attribute> AttributeList;

private:
    typedef search::SerialNum SerialNum;

    AttributeList _attributes;
    uint32_t      _docIdLimit;
    SerialNum     _currentSerialNum;

public:
    AttributeCollectionSpec(const AttributeList &attributes,
                            uint32_t docIdLimit,
                            SerialNum currentSerialNum);
    ~AttributeCollectionSpec();
    const AttributeList &getAttributes() const {
        return _attributes;
    }
    uint32_t getDocIdLimit() const {
        return _docIdLimit;
    }
    SerialNum getCurrentSerialNum() const {
        return _currentSerialNum;
    }
    bool hasAttribute(const vespalib::string &name) const {
        for (const auto &attr : _attributes) {
            if (attr.getName() == name) {
                return true;
            }
        }
        return false;
    }
};

} // namespace proton

