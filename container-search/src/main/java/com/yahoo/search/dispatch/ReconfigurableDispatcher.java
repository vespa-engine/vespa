package com.yahoo.search.dispatch;

import com.yahoo.component.ComponentId;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.messagebus.network.rpc.SlobrokConfigSubscriber;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;
import com.yahoo.yolean.UncheckedInterruptedException;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * @author jonmv
 */
public class ReconfigurableDispatcher extends Dispatcher {

    private final ConfigSubscriber subscriber;

    public ReconfigurableDispatcher(ComponentId clusterId, DispatchConfig dispatchConfig, VipStatus vipStatus) {
        super(clusterId, dispatchConfig, new DispatchNodesConfig.Builder().build(), vipStatus);
        this.subscriber = new ConfigSubscriber();
        this.subscriber.subscribe(this::updateWithNewConfig, DispatchNodesConfig.class, clusterId.stringValue());
    }

    @Override
    public void deconstruct() {
        subscriber.close();
        super.deconstruct();
    }

}
