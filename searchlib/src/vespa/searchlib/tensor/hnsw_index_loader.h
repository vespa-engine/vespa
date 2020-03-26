// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::fileutil { class LoadedBuffer; }

namespace search::tensor {

class HnswGraph;

class HnswIndexLoader {
public:
    HnswIndexLoader(HnswGraph &graph);
    ~HnswIndexLoader();
    bool load(const fileutil::LoadedBuffer& buf);
private:
    HnswGraph &_graph;
};

}
