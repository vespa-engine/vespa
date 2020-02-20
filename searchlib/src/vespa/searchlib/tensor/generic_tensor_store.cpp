// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_tensor_store.h"
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/macro.h>

using document::DeserializeException;
using search::datastore::Handle;
using vespalib::tensor::Tensor;
using vespalib::tensor::TypedBinaryFormat;

namespace search::tensor {

constexpr size_t MIN_BUFFER_ARRAYS = 1024;

GenericTensorStore::GenericTensorStore()
    : TensorStore(_concreteStore),
      _concreteStore(),
      _bufferType(RefType::align(1),
                  MIN_BUFFER_ARRAYS,
                  RefType::offsetSize() / RefType::align(1))
{
    _store.addType(&_bufferType);
    _store.initActiveBuffers();
}

GenericTensorStore::~GenericTensorStore()
{
    _store.dropBuffers();
}

std::pair<const void *, uint32_t>
GenericTensorStore::getRawBuffer(RefType ref) const
{
    if (!ref.valid()) {
        return std::make_pair(nullptr, 0u);
    }
    const char *buf = _store.getEntry<char>(ref);
    uint32_t len = *reinterpret_cast<const uint32_t *>(buf);
    return std::make_pair(buf + sizeof(uint32_t), len);
}

Handle<char>
GenericTensorStore::allocRawBuffer(uint32_t size)
{
    if (size == 0) {
        return Handle<char>();
    }
    size_t extSize = size + sizeof(uint32_t);
    size_t bufSize = RefType::align(extSize);
    auto result = _concreteStore.rawAllocator<char>(_typeId).alloc(bufSize);
    *reinterpret_cast<uint32_t *>(result.data) = size;
    char *padWritePtr = result.data + extSize;
    for (size_t i = extSize; i < bufSize; ++i) {
        *padWritePtr++ = 0;
    }
    // Hide length of buffer (first 4 bytes) from users of the buffer.
    return Handle<char>(result.ref, result.data + sizeof(uint32_t));
}

void
GenericTensorStore::holdTensor(EntryRef ref)
{
    if (!ref.valid()) {
        return;
    }
    RefType iRef(ref);
    const char *buf = _store.getEntry<char>(iRef);
    uint32_t len = *reinterpret_cast<const uint32_t *>(buf);
    _concreteStore.holdElem(ref, len + sizeof(uint32_t));
}

TensorStore::EntryRef
GenericTensorStore::move(EntryRef ref)
{
    if (!ref.valid()) {
        return RefType();
    }
    auto oldraw = getRawBuffer(ref);
    auto newraw = allocRawBuffer(oldraw.second);
    memcpy(newraw.data, oldraw.first, oldraw.second);
    _concreteStore.holdElem(ref, oldraw.second + sizeof(uint32_t));
    return newraw.ref;
}

std::unique_ptr<Tensor>
GenericTensorStore::getTensor(EntryRef ref) const
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
    return tensor;
}

TensorStore::EntryRef
GenericTensorStore::setTensor(const Tensor &tensor)
{
    vespalib::nbostream stream;
    TypedBinaryFormat::serialize(stream, tensor);
    auto raw = allocRawBuffer(stream.size());
    memcpy(raw.data, stream.peek(), stream.size());
    return raw.ref;
}

}
