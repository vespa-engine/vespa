// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search {
namespace fef {

/**
 * This class contains location data that is associated with a query.
 **/
class Location
{
private:
    vespalib::string _attr;
    int32_t     _xPos;
    int32_t     _yPos;
    uint32_t    _xAspect;
    bool        _valid;

public:
    /**
     * Creates an empty object.
     **/
    Location();

    /**
     * Sets the name of the attribute to use for x positions.
     *
     * @param  xAttr the attribute name.
     * @return this to allow chaining.
     **/
    Location &
    setAttribute(const vespalib::string & attr)
    {
        _attr = attr;
        return *this;
    }

    /**
     * Returns the name of the attribute to use for positions.
     *
     * @return the attribute name.
     **/
    const vespalib::string & getAttribute() const { return _attr; }

    /**
     * Sets the x position of this location.
     *
     * @param  xPos the x position.
     * @return this to allow chaining.
     **/
    Location & setXPosition(int32_t xPos) { _xPos = xPos; return *this; }

    /**
     * Returns the x position of this location.
     *
     * @return the x position.
     **/
    int32_t getXPosition() const { return _xPos; }

    /**
     * Sets the y position of this location.
     *
     * @param yPos the y position.
     * @return this to allow chaining.
     **/
    Location & setYPosition(int32_t yPos) { _yPos = yPos; return *this; }

    /**
     * Returns the y position of this location.
     *
     * @return the y position.
     **/
    int32_t getYPosition() const { return _yPos; }

    /**
     * Sets the x distance multiplier fraction.
     *
     * @param xAspect the x aspect.
     * @return this to allow chaining.
     **/
    Location & setXAspect(uint32_t xAspect) { _xAspect = xAspect; return *this; }

    /**
     * Returns the x distance multiplier fraction.
     *
     * @return the x aspect.
     **/
    uint32_t getXAspect() const { return _xAspect; }

    /**
     * Sets whether this is a valid location object.
     *
     * @param  valid true if this is valid.
     * @return this to allow chaining.
     **/
    Location & setValid(bool valid) { _valid = valid; return *this; }

    /**
     * Returns whether this is a valid location object.
     *
     * @param true if this is a valid.
     **/
    bool isValid() const { return _valid; }
};

} // namespace fef
} // namespace search

