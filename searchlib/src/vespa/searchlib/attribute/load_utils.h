// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "atomic_utils.h"
#include "readerbase.h"
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/searchcommon/attribute/multi_value_traits.h>

namespace vespalib::datastore {
    class AtomicEntryRef;
    class EntryRef;
}

namespace vespalib { class GenerationHolder; }

namespace search { class AttributeVector; }

namespace search::attribute {

/**
 * Helper functions used to open / load attribute vector data files from disk.
 */
class LoadUtils {
public:
    using FileInterfaceUP = std::unique_ptr<FastOS_FileInterface>;
    using LoadedBufferUP = std::unique_ptr<fileutil::LoadedBuffer>;

    static FileInterfaceUP openFile(const AttributeVector& attr, const vespalib::string& suffix);
    static FileInterfaceUP openDAT(const AttributeVector& attr);
    static FileInterfaceUP openIDX(const AttributeVector& attr);
    static FileInterfaceUP openWeight(const AttributeVector& attr);

    static bool file_exists(const AttributeVector& attr, const vespalib::string& suffix);
    static LoadedBufferUP loadFile(const AttributeVector& attr, const vespalib::string& suffix);

    static LoadedBufferUP loadDAT(const AttributeVector& attr);
    static LoadedBufferUP loadIDX(const AttributeVector& attr);
    static LoadedBufferUP loadWeight(const AttributeVector& attr);
    static LoadedBufferUP loadUDAT(const AttributeVector& attr);
};

/**
 * Function for loading mapping from document id to array of enum indexes
 * or values from enumerated attribute reader.
 */
template <class MvMapping, class Saver>
uint32_t
loadFromEnumeratedMultiValue(MvMapping &mapping,
                             ReaderBase &attrReader,
                             vespalib::ConstArrayRef<atomic_utils::NonAtomicValue_t<multivalue::ValueType_t<typename MvMapping::MultiValueType>>> enumValueToValueMap,
                             vespalib::ConstArrayRef<uint32_t> enum_value_remapping,
                             Saver saver) __attribute((noinline));

/**
 * Function for loading mapping from document id to enum index or
 * value from enumerated attribute reader.
 */
template <class Vector, class Saver>
void
loadFromEnumeratedSingleValue(Vector &vector,
                              vespalib::GenerationHolder &genHolder,
                              ReaderBase &attrReader,
                              vespalib::ConstArrayRef<atomic_utils::NonAtomicValue_t<typename Vector::ValueType>> enumValueToValueMap,
                              vespalib::ConstArrayRef<uint32_t> enum_value_remapping,
                              Saver saver) __attribute((noinline));

}
