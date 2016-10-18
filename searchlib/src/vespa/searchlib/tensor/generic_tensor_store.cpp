// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "generic_tensor_store.h"
#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/tensor/serialization/typed_binary_format.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/macro.h>
#include <vespa/document/util/serializable.h>
#include <vespa/searchlib/btree/datastore.hpp>

using vespalib::tensor::Tensor;
using vespalib::tensor::TypedBinaryFormat;
using document::DeserializeException;

namespace search {

namespace attribute {

constexpr size_t MIN_BUFFER_CLUSTERS = 1024;

GenericTensorStore::GenericTensorStore()
    : TensorStore()
{
}


GenericTensorStore::~GenericTensorStore()
{
}


std::pair<const void *, uint32_t>
GenericTensorStore::getRawBuffer(RefType ref) const
{
    if (!ref.valid()) {
        return std::make_pair(nullptr, 0u);
    }
    const char *buf = _store.getBufferEntry<char>(ref.bufferId(),
                                                  ref.offset());
    uint32_t len = *reinterpret_cast<const uint32_t *>(buf);
    return std::make_pair(buf + sizeof(uint32_t), len);
}


std::pair<void *, TensorStore::RefType>
GenericTensorStore::allocRawBuffer(uint32_t size)
{
    if (size == 0) {
        return std::make_pair(nullptr, RefType());
    }
    size_t extSize = size + sizeof(uint32_t);
    size_t bufSize = RefType::align(extSize);
    _store.ensureBufferCapacity(_typeId, bufSize);
    uint32_t activeBufferId = _store.getActiveBufferId(_typeId);
    btree::BufferState &state = _store.getBufferState(activeBufferId);
    size_t oldSize = state.size();
    char *bufferEntryWritePtr =
        _store.getBufferEntry<char>(activeBufferId, oldSize);
    *reinterpret_cast<uint32_t *>(bufferEntryWritePtr) = size;
    char *padWritePtr = bufferEntryWritePtr + extSize;
    for (size_t i = extSize; i < bufSize; ++i) {
        *padWritePtr++ = 0;
    }
    state.pushed_back(bufSize);
    return std::make_pair(bufferEntryWritePtr + sizeof(uint32_t),
                          RefType(oldSize, activeBufferId));
}

void
GenericTensorStore::holdTensor(RefType ref)
{
    if (!ref.valid()) {
        return;
    }
    const char *buf = _store.getBufferEntry<char>(ref.bufferId(),
                                                  ref.offset());
    uint32_t len = *reinterpret_cast<const uint32_t *>(buf);
    _store.holdElem(ref, len + sizeof(uint32_t));
}


TensorStore::RefType
GenericTensorStore::move(RefType ref) {
    if (!ref.valid()) {
        return RefType();
    }
    auto oldraw = getRawBuffer(ref);
    auto newraw = allocRawBuffer(oldraw.second);
    memcpy(newraw.first, oldraw.first, oldraw.second);
    _store.holdElem(ref, oldraw.second + sizeof(uint32_t));
    return newraw.second;
}

std::unique_ptr<Tensor>
GenericTensorStore::getTensor(RefType ref) const
{
    auto raw = getRawBuffer(ref);
    if (raw.second == 0u) {
        return std::unique_ptr<Tensor>();
    }
    vespalib::nbostream wrapStream(raw.first, raw.second);
    auto tensor = TypedBinaryFormat::deserialize(wrapStream);
    if (wrapStream.size() != 0) {
        throw DeserializeException("Leftover bytes deserializing "
                                   "tensor attribute value.",
                                   VESPA_STRLOC);
    }
    return std::move(tensor);
}


TensorStore::RefType
GenericTensorStore::setTensor(const Tensor &tensor)
{
    vespalib::nbostream stream;
    TypedBinaryFormat::serialize(stream, tensor);
    auto raw = allocRawBuffer(stream.size());
    memcpy(raw.first, stream.peek(), stream.size());
    return raw.second;
}

}  // namespace search::attribute

}  // namespace search
