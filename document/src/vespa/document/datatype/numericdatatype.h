// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::NumericDataType
 * \ingroup datatype
 *
 * \brief Data type holding numbers of various types.
 *
 * Data type object allowing you to store a number. This is typically only
 * created when initializing the global primitive datatypes in the DataType
 * class.
 */
#pragma once

#include <vespa/document/datatype/primitivedatatype.h>

namespace document {

class NumericDataType : public PrimitiveDataType {
public:
    NumericDataType(Type type);

        // Implementation of PrimitiveDataType
    virtual NumericDataType* clone() const
        { return new NumericDataType(*this); }
    virtual void print(std::ostream&, bool verbose,
                       const std::string& indent) const;

    DECLARE_IDENTIFIABLE_ABSTRACT(NumericDataType);
};

}


