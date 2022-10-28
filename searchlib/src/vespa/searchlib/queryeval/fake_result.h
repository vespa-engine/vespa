// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "posting_info.h"
#include <vespa/searchlib/common/feature.h>
#include <vespa/searchlib/common/fslimits.h>
#include <vector>
#include <memory>

namespace search::queryeval {

class FakeResult
{
public:
    struct Element {
        uint32_t              id;
        int32_t               weight;
        uint32_t              length;
        std::vector<uint32_t> positions;
        Element(uint32_t id_) : id(id_), weight(1),
                                length(SEARCHLIB_FEF_UNKNOWN_FIELD_LENGTH),
                                positions() {}
        bool operator==(const Element &rhs) const {
            return (id == rhs.id &&
                    weight == rhs.weight &&
                    length == rhs.length &&
                    positions == rhs.positions);
        }
    };

    struct Document {
        uint32_t             docId;
        std::vector<Element> elements;
        feature_t rawScore;
        uint32_t field_length;
        uint32_t num_occs;
        Document(uint32_t id) : docId(id), elements(), rawScore(0), field_length(0), num_occs(0) {}
        bool operator==(const Document &rhs) const {
            return (docId == rhs.docId &&
                    elements == rhs.elements &&
                    rawScore == rhs.rawScore &&
                    field_length == rhs.field_length &&
                    num_occs == rhs.num_occs);
        }
    };

private:
    std::vector<Document> _documents;
    std::shared_ptr<MinMaxPostingInfo> _minMaxPostingInfo;

public:
    FakeResult();
    FakeResult(const FakeResult &);
    ~FakeResult();
    FakeResult &operator=(const FakeResult &);

    FakeResult &doc(uint32_t docId) {
        _documents.push_back(Document(docId));
        return *this;
    }

    FakeResult &elem(uint32_t id) {
        _documents.back().elements.push_back(Element(id));
        return *this;
    }

    FakeResult &score(feature_t s) {
        _documents.back().rawScore = s;
        return *this;
    }

    FakeResult &len(uint32_t length) {
        if (_documents.back().elements.empty()) {
            elem(0);
        }
        _documents.back().elements.back().length = length;
        return *this;
    }

    FakeResult &weight(uint32_t w) {
        if (_documents.back().elements.empty()) {
            elem(0);
        }
        _documents.back().elements.back().weight = w;
        return *this;
    }

    FakeResult &pos(uint32_t p) {
        if (_documents.back().elements.empty()) {
            elem(0);
        }
        _documents.back().elements.back().positions.push_back(p);
        return *this;
    }

    FakeResult &minMax(int32_t minWeight, int32_t maxWeight) {
        _minMaxPostingInfo = std::make_shared<MinMaxPostingInfo>(minWeight, maxWeight);
        return *this;
    }

    FakeResult &field_length(uint32_t field_length_) {
        _documents.back().field_length = field_length_;
        return *this;
    }

    FakeResult &num_occs(uint32_t num_occs_) {
        _documents.back().num_occs = num_occs_;
        return *this;
    }

    bool operator==(const FakeResult &rhs) const {
        return _documents == rhs._documents;
    }

    const std::vector<Document> &inspect() const { return _documents; }

    const PostingInfo *postingInfo() const { return _minMaxPostingInfo.get(); }
};

std::ostream &operator << (std::ostream &out, const FakeResult &result);

}
