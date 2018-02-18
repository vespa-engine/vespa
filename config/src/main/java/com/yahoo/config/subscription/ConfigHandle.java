// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;


import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.impl.ConfigSubscription;

/**
 * A config handle represents one config in the context of one active subscription on a {@link ConfigSubscriber}.
 * It will contain meta data of the subscription of that particular config, as well as access to the {@link com.yahoo.config.ConfigInstance} itself.
 *
 * @param <T> the type of the config
 * @author vegardh
 * @since 5.1
 */
public class ConfigHandle<T extends ConfigInstance> {

    private final ConfigSubscription<T> sub;
    private boolean changed = false;

    protected ConfigHandle(ConfigSubscription<T> sub) {
        this.sub = sub;
    }

    /**
     * Returns true if:
     *
     * The config generation for the {@link ConfigSubscriber} that produced this is the first one in its life cycle. (Typically first time config.)
     * or
     * All configs for the subscriber have a new generation since the last time nextConfig() was called
     * AND it's the same generation AND there is a change in <strong>this</strong> handle's config.
     * (Typically calls for a reconfig.)
     *
     * @return there is a new config
     */
    public boolean isChanged() {
        return changed;
    }

    void setChanged(boolean changed) {
        this.changed = changed;
    }

    ConfigSubscription<T> subscription() {
        return sub;
    }

    /**
     * The config of this handle
     *
     * @return the config that this handle holds
     */
    public T getConfig() {
        // TODO throw if subscriber not frozen?
        return sub.getConfig();
    }

    @Override
    public String toString() {
        return "Handle changed: " + changed + "\nSub:\n" + sub.toString();
    }

}
