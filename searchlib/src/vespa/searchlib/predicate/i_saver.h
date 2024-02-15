// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search { class BufferWriter; }

namespace search::predicate {

/*
 * Interface class for saving (parts of) predicate index.
 */
class ISaver {
public:
    virtual ~ISaver() = default;
    virtual void save(BufferWriter& writer) const = 0;
};

}
