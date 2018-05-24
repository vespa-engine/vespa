// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search { namespace query { class Node; } }

namespace proton::matching {

class ProtonTermData;

const ProtonTermData *termDataFromNode(const search::query::Node &node);

}

