package com.yahoo.search.dispatch;

import com.yahoo.component.ComponentId;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.container.QrConfig;
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

    @Inject
    public ReconfigurableDispatcher(ComponentId clusterId, DispatchConfig dispatchConfig, QrConfig qrConfig, VipStatus vipStatus) {
        super(clusterId, dispatchConfig, new DispatchNodesConfig.Builder().build(), vipStatus);
        this.subscriber = new ConfigSubscriber();
        this.subscriber.subscribe(this::updateWithNewConfig, DispatchNodesConfig.class, configId(clusterId, qrConfig));
    }

    @Override
    public void deconstruct() {
        subscriber.close();
        super.deconstruct();
    }

    private static String configId(ComponentId clusterId, QrConfig qrConfig) {
        return qrConfig.clustername() + "/component/" + clusterId.getName();
    }

}
