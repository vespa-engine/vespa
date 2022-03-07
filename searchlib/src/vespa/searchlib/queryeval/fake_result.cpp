// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_result.h"
#include <ostream>

namespace search::queryeval {

FakeResult::FakeResult()
    : _documents(),
      _minMaxPostingInfo()
{
}

FakeResult::FakeResult(const FakeResult &) = default;

FakeResult::~FakeResult() = default;

FakeResult &
FakeResult::operator=(const FakeResult &) = default;

std::ostream &operator << (std::ostream &out, const FakeResult &result) {
    const std::vector<FakeResult::Document> &doc = result.inspect();
    if (doc.size() == 0) {
        out << std::endl << "empty" << std::endl;
    } else {
        out << std::endl;
        for (size_t d = 0; d < doc.size(); ++d) {
            out << "{ DOC id: " << doc[d].docId << " }" << std::endl;

            const std::vector<FakeResult::Element> &elem = doc[d].elements;
            for (size_t e = 0; e < elem.size(); ++e) {
                out << "  ( ELEM id: " << elem[e].id
                    << " weight: " << elem[e].weight
                    << " len: " << elem[e].length
                    << " )" << std::endl;

                const std::vector<uint32_t> &pos = elem[e].positions;
                for (size_t p = 0; p < pos.size(); ++p) {
                    out << "    [ OCC pos: " << pos[p] << " ]" << std::endl;
                }
            }
            out << "  ( RAW score: " << doc[d].rawScore << " )" << std::endl;
        }
    }
    return out;
}

}
