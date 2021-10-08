// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/juniper/IJuniperProperties.h>
#include <map>

namespace search::docsummary {

class JuniperProperties : public IJuniperProperties {
private:
    std::map<vespalib::string, vespalib::string> _properties;

    /**
     * Resets the property map to all default values. This is used for the empty constructor and also called before
     * retrieving configured properties.
     */
    void reset();


public:
    /**
     * Constructs a juniper property object with default values set.
     */
    JuniperProperties();
    /**
     * Constructs a juniper property object with default values set.
     */
    JuniperProperties(const vespa::config::search::summary::JuniperrcConfig &cfg);

    /**
     * Destructor. Frees any allocated resources.
     */
    virtual ~JuniperProperties();

    /**
     * This method subscribes to config from the given configuration id. This does the necessary mapping from
     * user-friendly configuration parameters to juniper specific properties. Note that no exceptions thrown by the
     * configuration framework are caught in this method. Please refer to the config framework for details on what to
     * expect.
     *
     * @param configId The config id to subscribe to.
     */
    void subscribe(const char *configId);

    /**
     * Implements configure callback for config subscription.
     *
     * @param cfg The configuration object.
     */
    void configure(const vespa::config::search::summary::JuniperrcConfig &cfg);

    // Inherit doc from IJuniperProperties.
    const char *GetProperty(const char *name, const char *def = NULL) override;
};

}
