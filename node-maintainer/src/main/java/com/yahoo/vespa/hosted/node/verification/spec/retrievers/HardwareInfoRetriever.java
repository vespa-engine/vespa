// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.VerifierSettings;

import java.util.ArrayList;

/**
 * Created by olaa on 30/06/2017.
 * Makes a HardwareInfo object and calls all the retrievers for this object.
 */
public class HardwareInfoRetriever {

    public static HardwareInfo retrieve(CommandExecutor commandExecutor, VerifierSettings verifierSettings) {
        HardwareInfo hardwareInfo = new HardwareInfo();
        ArrayList<HardwareRetriever> infoList = new ArrayList<>();
        infoList.add(new CPURetriever(hardwareInfo, commandExecutor));
        infoList.add(new MemoryRetriever(hardwareInfo, commandExecutor));
        infoList.add(new DiskRetriever(hardwareInfo, commandExecutor));
        infoList.add(new NetRetriever(hardwareInfo, commandExecutor, verifierSettings));

        for (HardwareRetriever hardwareInfoType : infoList) {
            hardwareInfoType.updateInfo();
        }
        return hardwareInfo;
    }

}
