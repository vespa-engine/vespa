// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

/** An abstract interface to configuration file settings used by Juniper to process
 *  it's preconfigured parameter sets.
 */
class IJuniperProperties
{
public:
    /** Get the value of a property
     *  @param name The textual representation of the property
     *  assumed to be on the form class.juniperpart.variable, such as for example
     *  juniper.dynsum.length
     *  @param def A default value for the property if not found in configuration
     *  @return The value of the property or @param def if no such property is set
     */
    virtual const char* GetProperty(const char* name, const char* def = nullptr) const = 0;

    virtual ~IJuniperProperties() = default;
};
