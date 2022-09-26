// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "load_utils.hpp"
#include "i_enum_store.h"
#include "loadedenumvalue.h"
#include "multi_value_mapping.h"
#include <vespa/fastos/file.h>
#include <vespa/searchcommon/attribute/multivalue.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/array.hpp>

using search::multivalue::WeightedValue;
using vespalib::datastore::AtomicEntryRef;

namespace search::attribute {

using FileInterfaceUP = LoadUtils::FileInterfaceUP;
using LoadedBufferUP = LoadUtils::LoadedBufferUP;

FileInterfaceUP
LoadUtils::openFile(const AttributeVector& attr, const vespalib::string& suffix)
{
    return FileUtil::openFile(attr.getBaseFileName() + "." + suffix);
}

FileInterfaceUP
LoadUtils::openDAT(const AttributeVector& attr)
{
    return openFile(attr, "dat");
}

FileInterfaceUP
LoadUtils::openIDX(const AttributeVector& attr)
{
    return openFile(attr, "idx");
}

FileInterfaceUP
LoadUtils::openWeight(const AttributeVector& attr)
{
    return openFile(attr, "weight");
}

bool
LoadUtils::file_exists(const AttributeVector& attr, const vespalib::string& suffix)
{
    return vespalib::fileExists(attr.getBaseFileName() + "." + suffix);
}

LoadedBufferUP
LoadUtils::loadFile(const AttributeVector& attr, const vespalib::string& suffix)
{
    return FileUtil::loadFile(attr.getBaseFileName() + "." + suffix);
}

LoadedBufferUP
LoadUtils::loadDAT(const AttributeVector& attr)
{
    return loadFile(attr, "dat");
}

LoadedBufferUP
LoadUtils::loadIDX(const AttributeVector& attr)
{
    return loadFile(attr, "idx");
}

LoadedBufferUP
LoadUtils::loadWeight(const AttributeVector& attr)
{
    return loadFile(attr, "weight");
}

LoadedBufferUP
LoadUtils::loadUDAT(const AttributeVector& attr)
{
    return loadFile(attr, "udat");
}


#define INSTANTIATE_ARRAY(ValueType, Saver) \
template uint32_t loadFromEnumeratedMultiValue(MultiValueMapping<ValueType>&, ReaderBase &, vespalib::ConstArrayRef<atomic_utils::NonAtomicValue_t<ValueType>>, vespalib::ConstArrayRef<uint32_t>, Saver)
#define INSTANTIATE_WSET(ValueType, Saver) \
template uint32_t loadFromEnumeratedMultiValue(MultiValueMapping<WeightedValue<ValueType>> &, ReaderBase &, vespalib::ConstArrayRef<atomic_utils::NonAtomicValue_t<ValueType>>, vespalib::ConstArrayRef<uint32_t>, Saver)
#define INSTANTIATE_SINGLE(ValueType, Saver) \
template void loadFromEnumeratedSingleValue(vespalib::RcuVectorBase<ValueType> &, vespalib::GenerationHolder &, ReaderBase &, vespalib::ConstArrayRef<atomic_utils::NonAtomicValue_t<ValueType>>, vespalib::ConstArrayRef<uint32_t>, Saver)

#define INSTANTIATE_SINGLE_ARRAY_WSET(ValueType, Saver) \
INSTANTIATE_SINGLE(ValueType, Saver); \
INSTANTIATE_ARRAY(ValueType, Saver); \
INSTANTIATE_WSET(ValueType, Saver)

#define INSTANTIATE_ENUM(Saver) \
INSTANTIATE_SINGLE_ARRAY_WSET(AtomicEntryRef, Saver)

#define INSTANTIATE_VALUE(ValueType) \
INSTANTIATE_SINGLE_ARRAY_WSET(ValueType, NoSaveLoadedEnum)

INSTANTIATE_ENUM(SaveLoadedEnum); // posting lists
INSTANTIATE_ENUM(SaveEnumHist);   // no posting lists but still enumerated
INSTANTIATE_VALUE(int8_t);
INSTANTIATE_VALUE(int16_t);
INSTANTIATE_VALUE(int32_t);
INSTANTIATE_VALUE(int64_t);
INSTANTIATE_VALUE(float);
INSTANTIATE_VALUE(double);

}
