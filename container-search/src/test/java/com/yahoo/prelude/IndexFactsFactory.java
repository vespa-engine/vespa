// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.ConfigInstance;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.container.QrSearchersConfig;

/**
 * @author Simon Thoresen Hult
 */
public abstract class IndexFactsFactory {

    public static IndexFacts newInstance(String configId) {
        return new IndexFacts(new IndexModel(resolveConfig(IndexInfoConfig.class, configId),
                                             resolveConfig(QrSearchersConfig.class, configId)));

    }

    public static IndexFacts newInstance(String indexInfoConfigId, String qrSearchersConfigId) {
        return new IndexFacts(new IndexModel(resolveConfig(IndexInfoConfig.class, indexInfoConfigId),
                                             resolveConfig(QrSearchersConfig.class, qrSearchersConfigId)));

    }

    @SuppressWarnings("deprecation")
    private static <T extends ConfigInstance> T resolveConfig(Class<T> configClass, String configId) {
        if (configId == null) return null;
        return ConfigGetter.getConfig(configClass, configId);
    }

}
