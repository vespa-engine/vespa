// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "basic_nodes.h"

namespace vespalib::eval {

/**
 * Function used to delete an AST with arbitrary depth without
 * overflowing the stack. This is needed because the AST is not
 * compacted in any way and large expressions will produce very deep
 * ASTs.
 **/
void delete_node(nodes::Node_UP node);

}
