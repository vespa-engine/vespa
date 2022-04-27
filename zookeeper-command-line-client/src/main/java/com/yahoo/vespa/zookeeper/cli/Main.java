// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper.cli;

import com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder;
import org.apache.zookeeper.ZooKeeperMain;
import org.apache.zookeeper.util.ServiceUtils;
import org.slf4j.impl.SimpleLogger;
import java.io.IOException;

/**
 * @author bjorncs
 */
public class Main {

    static {
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "WARN");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        new ZkClientConfigBuilder()
                .toConfigProperties()
                .forEach(System::setProperty);
        ServiceUtils.setSystemExitProcedure(System::exit);
        ZooKeeperMain.main(args);
    }
}
