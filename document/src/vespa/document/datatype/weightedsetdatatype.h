// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::WeightedSetDataType
 * \ingroup datatype
 *
 * \brief DataType describing a weighted set.
 *
 * Describes what can be stored and behaviour of weighted sets with this type.
 * The create if non-existing and remove if zero weight functionality, as used
 * in tagging, is a part of the type.
 */
#pragma once

#include <vespa/document/datatype/collectiondatatype.h>

namespace document {

class WeightedSetDataType : public CollectionDataType {
    bool _createIfNonExistent;
    bool _removeIfZero;

public:
    WeightedSetDataType() {}
    WeightedSetDataType(const DataType& nestedType,
                        bool createIfNonExistent, bool removeIfZero);
    WeightedSetDataType(const DataType& nestedType,
                        bool createIfNonExistent, bool removeIfZero, int id);

    /**
     * @return Whether values of this datatype will autogenerate entries if
     * operations that require an existing entries operates on non-existing
     * ones.
     */
    bool createIfNonExistent() const { return _createIfNonExistent; };
    /**
     * @return Whether values of this datatype will automatically
     *         remove entries with zero weight.
     */
    bool removeIfZero() const { return _removeIfZero; };

        // CollectionDataType implementation
    virtual std::unique_ptr<FieldValue> createFieldValue() const;
    virtual void print(std::ostream&, bool verbose,
                       const std::string& indent) const;
    virtual bool operator==(const DataType& other) const;
    virtual WeightedSetDataType* clone() const
        { return new WeightedSetDataType(*this); }

    FieldPath::UP onBuildFieldPath(const vespalib::stringref &remainFieldName) const;

    DECLARE_IDENTIFIABLE(WeightedSetDataType);
};

} // document

