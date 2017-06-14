// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/datatype/datatype.h>

namespace search {
namespace docsummary {

struct SearchDataType {
    static const document::DataType *URI;
};

}  // namespace search::docsummary
}  // namespace search
