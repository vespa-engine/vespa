// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/util/shared_string_repo.h>

namespace vespalib::eval {

 /**
  *  Implements Value::Index by reading a stream of serialized
  *  labels.
  **/
class StreamedValueIndex : public Value::Index
{
public:

    struct SerializedDataRef {
        uint32_t num_mapped_dims;
        uint32_t num_subspaces;
        const std::vector<label_t> labels;
    };
    StreamedValueIndex(uint32_t num_mapped_dims, uint32_t num_subspaces, const std::vector<label_t> &labels_in)
      : _data{num_mapped_dims, num_subspaces, labels_in}
    {}

    // index API:
    size_t size() const override { return _data.num_subspaces; }
    std::unique_ptr<View> create_view(const std::vector<size_t> &dims) const override;

    // NB NOTE WARNING XXX: simply serializing the handle view and
    // discarding the backing streamed value will result in dangling
    // string enum value usage when the value is later deserialized.
    SerializedDataRef get_data_reference() const { return _data; }

private:
    SerializedDataRef _data;
};

} // namespace

