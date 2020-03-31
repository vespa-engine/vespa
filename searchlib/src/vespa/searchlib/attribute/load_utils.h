// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"
#include "readerbase.h"
#include <vespa/vespalib/util/arrayref.h>

namespace search::attribute {

/**
 * Helper functions used to open / load attribute vector data files from disk.
 */
class LoadUtils {
public:
    using FileInterfaceUP = std::unique_ptr<FastOS_FileInterface>;
    using LoadedBufferUP = std::unique_ptr<fileutil::LoadedBuffer>;

private:
    static FileInterfaceUP openFile(const AttributeVector& attr, const vespalib::string& suffix);

public:
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
                             vespalib::ConstArrayRef<typename MvMapping::MultiValueType::ValueType> enumValueToValueMap,
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
                              vespalib::ConstArrayRef<typename Vector::ValueType> enumValueToValueMap,
                              Saver saver) __attribute((noinline));

}
