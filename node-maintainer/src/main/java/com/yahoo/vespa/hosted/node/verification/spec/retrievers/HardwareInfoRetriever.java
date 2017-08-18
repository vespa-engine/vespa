package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.VerifierSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes a HardwareInfo object and calls all the retrievers for this object.
 *
 * @author olaaun
 */
public class HardwareInfoRetriever {

    public static HardwareInfo retrieve(CommandExecutor commandExecutor, VerifierSettings verifierSettings) {
        HardwareInfo hardwareInfo = new HardwareInfo();
        List<HardwareRetriever> infoList = new ArrayList<>();
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
