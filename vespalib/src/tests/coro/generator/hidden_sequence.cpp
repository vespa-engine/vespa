// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hidden_sequence.h"

namespace {

struct MyHiddenSeq : vespalib::Sequence<size_t> {
    const std::vector<size_t> &data;
    size_t pos;
    MyHiddenSeq(const std::vector<size_t> &data_in)
      : data(data_in), pos(0) {}
    bool valid() const override { return pos < data.size(); }
    size_t get() const override { return data[pos]; }
    void next() override { ++pos; }
};

}

vespalib::Sequence<size_t>::UP make_ext_seq(const std::vector<size_t> &data) {
    return std::make_unique<MyHiddenSeq>(data);
}
