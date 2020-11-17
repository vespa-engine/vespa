
#include "streamed_value_store.h"
#include "tensor_deserialize.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/streamed/streamed_value_builder_factory.h>
#include <vespa/eval/streamed/streamed_value_view.h>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.streamed_value_store");

using vespalib::datastore::Handle;
using namespace vespalib::eval;

namespace search::tensor {

namespace {

constexpr size_t MIN_BUFFER_ARRAYS = 1024;

struct CellsMemBlock {
    uint32_t num;
    uint32_t total_sz;
    const char *ptr;
    CellsMemBlock(TypedCells cells)
      : num(cells.size),
        total_sz(CellTypeUtils::mem_size(cells.type, num)),
        ptr((const char *)cells.data)
    {}
};

template<typename T>
T *fix_alignment(T *ptr, size_t align)
{
    static_assert(sizeof(T) == 1);
    assert((align & (align-1)) == 0); // must be 2^N
    size_t ptr_val = (size_t)ptr;
    size_t unalign = ptr_val & (align - 1);
    if (unalign == 0) {
        return ptr;
    } else {
        return ptr + (align - unalign);
    }
}

} // namespace <unnamed>

StreamedValueStore::StreamedValueStore(const ValueType &tensor_type)
  : TensorStore(_concreteStore),
    _concreteStore(),
    _bufferType(RefType::align(1),
                MIN_BUFFER_ARRAYS,
                RefType::offsetSize() / RefType::align(1)),
    _tensor_type(tensor_type),
    _data_from_type(_tensor_type)
{
    _store.addType(&_bufferType);
    _store.initActiveBuffers();
}

StreamedValueStore::~StreamedValueStore()
{
    _store.dropBuffers();
}

std::pair<const char *, uint32_t>
StreamedValueStore::getRawBuffer(RefType ref) const
{
    if (!ref.valid()) {
        return std::make_pair(nullptr, 0u);
    }
    const char *buf = _store.getEntry<char>(ref);
    uint32_t len = *reinterpret_cast<const uint32_t *>(buf);
    return std::make_pair(buf + sizeof(uint32_t), len);
}

Handle<char>
StreamedValueStore::allocRawBuffer(uint32_t size)
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
StreamedValueStore::holdTensor(EntryRef ref)
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
StreamedValueStore::move(EntryRef ref)
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

StreamedValueStore::StreamedValueData
StreamedValueStore::get_tensor_data(EntryRef ref) const
{
    StreamedValueData retval;
    retval.valid = false;
    auto raw = getRawBuffer(ref);
    if (raw.second == 0u) {
        return retval;
    }
    vespalib::nbostream_longlivedbuf source(raw.first, raw.second);
    uint32_t num_cells = source.readValue<uint32_t>();
    {
        uint32_t alignment = CellTypeUtils::alignment(_data_from_type.cell_type);
        const char *aligned_ptr = fix_alignment(source.peek(), alignment);
        size_t adjustment = aligned_ptr - source.peek();
        source.adjustReadPos(adjustment);
    }
    retval.cells_ref = TypedCells(source.peek(), _data_from_type.cell_type, num_cells);
    source.adjustReadPos(CellTypeUtils::mem_size(_data_from_type.cell_type, num_cells));
    retval.num_subspaces = source.readValue<uint32_t>();
    retval.labels_buffer = vespalib::ConstArrayRef<char>(source.peek(), source.size());
 
    if (retval.num_subspaces * _data_from_type.dense_subspace_size == num_cells) {
        retval.valid = true;
        return retval;
    }
    LOG(warning, "inconsistent stored tensor data: "
        "num_subspaces[%zu] * dense_subspace_size[%u] = %zu != num_cells[%u]",
        retval.num_subspaces, _data_from_type.dense_subspace_size,
        retval.num_subspaces * _data_from_type.dense_subspace_size,
        num_cells);
    return retval;
}

bool
StreamedValueStore::encode_tensor(EntryRef ref, vespalib::nbostream &target) const
{
    if (auto data = get_tensor_data(ref)) {
        StreamedValueView value(
            _tensor_type, _data_from_type.num_mapped_dimensions,
            data.cells_ref, data.num_subspaces, data.labels_buffer);
        vespalib::eval::encode_value(value, target);
        return true;
    } else {
        return false;
    }
}

void
StreamedValueStore::my_encode(const Value::Index &index,
                              vespalib::nbostream &target) const
{
    uint32_t num_subspaces = index.size();
    target << num_subspaces;
    uint32_t num_mapped_dims = _data_from_type.num_mapped_dimensions;
    std::vector<vespalib::stringref> labels(num_mapped_dims * num_subspaces);
    auto view = index.create_view({});
    view->lookup({});
    std::vector<vespalib::stringref> addr(num_mapped_dims);
    std::vector<vespalib::stringref *> addr_refs;
    for (auto & label : addr) {
        addr_refs.push_back(&label);
    }
    size_t subspace;
    for (size_t ss = 0; ss < num_subspaces; ++ss) {
        bool ok = view->next_result(addr_refs, subspace);
        assert(ok);
        size_t idx = subspace * num_mapped_dims;
        for (auto label : addr) {
            labels[idx++] = label;
        }
    }
    bool ok = view->next_result(addr_refs, subspace);
    assert(!ok);
    for (auto label : labels) {
        target.writeSmallString(label);
    }
}

TensorStore::EntryRef
StreamedValueStore::store_tensor(const Value &tensor)
{
    if (tensor.type() == _tensor_type) {
        CellsMemBlock cells_mem(tensor.cells());
        size_t alignment = CellTypeUtils::alignment(_data_from_type.cell_type);
        size_t padding = alignment - 1;
        vespalib::nbostream stream;
        stream << uint32_t(cells_mem.num);
        my_encode(tensor.index(), stream);
        size_t mem_size = stream.size() + cells_mem.total_sz + padding;
        auto raw = allocRawBuffer(mem_size);
        char *target = raw.data;
        memcpy(target, stream.peek(), sizeof(uint32_t));
        stream.adjustReadPos(sizeof(uint32_t));
        target += sizeof(uint32_t);
        target = fix_alignment(target, alignment);
        memcpy(target, cells_mem.ptr, cells_mem.total_sz);
        target += cells_mem.total_sz;
        memcpy(target, stream.peek(), stream.size());
        target += stream.size();
        assert(target <= raw.data + mem_size);
        return raw.ref;
    } else {
        LOG(error, "trying to store tensor of type %s in store only allowing %s",
            tensor.type().to_spec().c_str(), _tensor_type.to_spec().c_str());
        TensorStore::EntryRef invalid;
        return invalid;
    }
}

TensorStore::EntryRef
StreamedValueStore::store_encoded_tensor(vespalib::nbostream &encoded)
{
    const auto &factory = StreamedValueBuilderFactory::get();
    auto val = vespalib::eval::decode_value(encoded, factory);
    return store_tensor(*val);
}

}
