// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
private:
    uint32_t _num_mapped_dims;
    uint32_t _num_subspaces;
    const StringIdVector &_labels_ref;

public:
    StreamedValueIndex(uint32_t num_mapped_dims, uint32_t num_subspaces, const StringIdVector &labels_ref)
        : _num_mapped_dims(num_mapped_dims),
          _num_subspaces(num_subspaces),
          _labels_ref(labels_ref)
    {}

    // index API:
    size_t size() const override { return _num_subspaces; }
    std::unique_ptr<View> create_view(ConstArrayRef<size_t> dims) const override;
};

} // namespace
