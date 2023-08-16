package com.yahoo.search.dispatch;

import com.yahoo.component.ComponentId;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.container.QrConfig;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;
import com.yahoo.yolean.UncheckedInterruptedException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author jonmv
 */
public class ReconfigurableDispatcher extends Dispatcher {

    private final ConfigSubscriber subscriber;

    @Inject
    public ReconfigurableDispatcher(ComponentId clusterId, DispatchConfig dispatchConfig, QrConfig qrConfig, VipStatus vipStatus) {
        super(clusterId, dispatchConfig, new DispatchNodesConfig.Builder().build(), vipStatus);
        this.subscriber = new ConfigSubscriber();
        CountDownLatch configured = new CountDownLatch(1);
        this.subscriber.subscribe(config -> { updateWithNewConfig(config); configured.countDown(); },
                                  DispatchNodesConfig.class, configId(clusterId, qrConfig));
        try {
            if ( ! configured.await(1, TimeUnit.MINUTES))
                throw new IllegalStateException("timed out waiting for initial dispatch nodes config for " + clusterId.getName());
        }
        catch (InterruptedException e) {
            throw new UncheckedInterruptedException("interrupted waiting for initial dispatch nodes config for " + clusterId.getName(), e);
        }
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
