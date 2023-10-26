// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/sequence.h>
#include <vector>

vespalib::Sequence<size_t>::UP make_ext_seq(const std::vector<size_t> &data);
