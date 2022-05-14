// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/vsm/common/document.h>

namespace vsm {

/**
 * Interface for classes that want to modify a field value.
 **/
class FieldModifier
{
public:
    typedef std::unique_ptr<FieldModifier> UP;

    /**
     * Modifies the given field value and returns a new one.
     **/
    virtual document::FieldValue::UP modify(const document::FieldValue & fv) = 0;

    /**
     * Modifies the given field value and returns a new one.
     * Use the given field path to iterate the field value.
     **/
    virtual document::FieldValue::UP modify(const document::FieldValue & fv,
                                            const document::FieldPath & path) = 0;

    virtual ~FieldModifier() { }
};

typedef vespalib::hash_map<FieldIdT, FieldModifier::UP> FieldModifierMapT;

/**
 * This class wraps a map from field id to field modifier.
 **/
class FieldModifierMap
{
private:
    FieldModifierMapT _map;

public:
    FieldModifierMap();
    ~FieldModifierMap();
    FieldModifierMapT & map() { return _map; }
    const FieldModifierMapT & map() const { return _map; }

    /**
     * Returns the modifier associated with the given field id or NULL if not found.
     *
     * @param fId the field id to look up.
     * @return the field modifier or NULL if not found.
     **/
    FieldModifier * getModifier(FieldIdT fId) const;
};

}

