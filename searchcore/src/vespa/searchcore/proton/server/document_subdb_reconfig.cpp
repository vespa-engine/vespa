// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_subdb_reconfig.h"

namespace proton {

DocumentSubDBReconfig::DocumentSubDBReconfig(std::shared_ptr<Matchers> matchers_in)
    : _old_matchers(matchers_in),
      _new_matchers(matchers_in)
{
}

}

