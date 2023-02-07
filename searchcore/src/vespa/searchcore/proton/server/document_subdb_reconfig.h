// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <memory>

namespace proton {

class Matchers;

/**
 * Class representing the result of the prepare step of a IDocumentSubDB reconfig.
 */
class DocumentSubDBReconfig {
private:
    std::shared_ptr<Matchers> _old_matchers;
    std::shared_ptr<Matchers> _new_matchers;

public:
    DocumentSubDBReconfig(std::shared_ptr<Matchers> matchers_in);
    ~DocumentSubDBReconfig();

    void set_matchers(std::shared_ptr<Matchers> value) {
        _new_matchers = std::move(value);
    }
    bool has_matchers_changed() const noexcept {
        return _old_matchers != _new_matchers;
    }
    std::shared_ptr<Matchers> matchers() const { return _new_matchers; }

    void complete(uint32_t docid_limit, search::SerialNum serial_num);
};

}
