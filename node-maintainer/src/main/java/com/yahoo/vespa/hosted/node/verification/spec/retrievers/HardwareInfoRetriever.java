package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;

import java.util.ArrayList;

/**
 * Created by olaa on 30/06/2017.
 */
public class HardwareInfoRetriever {

    public static HardwareInfo retrieve(){
        HardwareInfo hardwareInfo = new HardwareInfo();
        CommandExecutor commandExecutor = new CommandExecutor();
        ArrayList<HardwareRetriever> infoList = new ArrayList<>();
        infoList.add(new CPURetriever(hardwareInfo, commandExecutor));
        infoList.add(new MemoryRetriever(hardwareInfo, commandExecutor));
        infoList.add(new DiskRetriever(hardwareInfo, commandExecutor));
        infoList.add(new NetRetriever(hardwareInfo, commandExecutor));

        for(HardwareRetriever hardwareInfoType : infoList) {
            hardwareInfoType.updateInfo();
        }
        return hardwareInfo;

    }

}
