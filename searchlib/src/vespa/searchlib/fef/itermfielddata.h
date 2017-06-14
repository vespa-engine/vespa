// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "handle.h"

namespace search {
namespace fef {

/**
 * Interface to information about a single field that is being
 * searched for a term (described by the ITermData interface). The
 * field may be either an index field or an attribute field. If more
 * information about the field is needed, the field id may be used to
 * consult the index environment.
 **/
class ITermFieldData
{
protected:
    virtual ~ITermFieldData() {}

public:
    /**
     * Obtain the global field id.
     *
     * @return field id
     **/
    virtual uint32_t getFieldId() const = 0;

    /**
     * Obtain the document frequency. This is a value between 0 and 1
     * indicating the ratio of the matching documents to the corpus.
     *
     * @return document frequency
     **/
    virtual double getDocFreq() const = 0;

    /**
     * Obtain the match handle for this field.
     *
     * @return match handle (or IllegalHandle)
     **/
    virtual TermFieldHandle getHandle() const = 0;
};

} // namespace fef
} // namespace search

