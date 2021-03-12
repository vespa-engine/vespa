// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper.cli;

import com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder;
import org.apache.zookeeper.ZooKeeperMain;

import java.io.IOException;

/**
 * @author bjorncs
 */
public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        new ZkClientConfigBuilder()
                .toConfigProperties()
                .forEach(System::setProperty);
        ZooKeeperMain.main(args);
    }
}
