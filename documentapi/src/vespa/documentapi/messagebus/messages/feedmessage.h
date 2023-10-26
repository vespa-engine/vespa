// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentmessage.h"

namespace documentapi {

class FeedMessage : public DocumentMessage {
private:
    string _name;
    int    _generation;
    int    _increment;

public:
    /**
     * Convenience typedef.
     */
    using UP = std::unique_ptr<FeedMessage>;
    using SP = std::shared_ptr<FeedMessage>;

public:
    /**
     * Constructs a new document message for deserialization.
     */
    FeedMessage();

    /**
     * Constructs a new feed message.
     *
     * @param name The feed label.
     * @param generation The feed generation.
     * @param increment The feed increment.
     */
    FeedMessage(const string& name, int generation, int increment);

    /**
     * Returns the name of this feed.
     *
     * @return The name.
     */
    const string& getName() const { return _name; }

    /**
     * Sets the name of this feed.
     *
     * @param name The name to set.
     */
    void setName(const string& name) { _name = name; }

    /**
     * Returns the generation of this feed.
     *
     * @return The generation.
     */
    int getGeneration() const { return _generation; }

    /**
     * Sets the generation of this feed.
     *
     * @param generation The generation to set.
     */
    void setGeneration(int generation) { _generation = generation; }

    /**
     * Returns the increment of this feed.
     *
     * @return The increment.
     */
    int getIncrement() const { return _increment; };

    /**
     * Sets the increment of this feed.
     *
     * @param increment The increment to set.
     */
    void setIncrement(int increment) { _increment = increment; };
};

}

