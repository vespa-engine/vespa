package com.yahoo.vespa.hosted.node.verification.spec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.*;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeGenerator;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeJsonModel;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.spec.yamasreport.YamasSpecReport;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by olaa on 14/07/2017.
 */
public class SpecVerifier {

    public void verifySpec(String zoneHostName){
        URL nodeRepoUrl;
        try {
            HostURLGenerator hostURLGenerator = new HostURLGenerator();
            nodeRepoUrl = hostURLGenerator.generateNodeInfoUrl(zoneHostName);
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
            return;
        }
        NodeJsonModel nodeJsonModel = NodeInfoRetriever.retrieve(nodeRepoUrl);
        HardwareInfo node = NodeGenerator.convertJsonModel(nodeJsonModel);
        HardwareInfo actualHardware = HardwareInfoRetriever.retrieve();
        YamasSpecReport yamasSpecReport = HardwareNodeComparator.compare(node, actualHardware);
        IPAddressVerifier ipAddressVerifier = new IPAddressVerifier();
        ipAddressVerifier.reportFaultyIpAddresses(nodeJsonModel, yamasSpecReport);

        printResults(yamasSpecReport);
    }

    private void printResults(YamasSpecReport yamasSpecReport) {
        //TODO: Instead of println, report JSON to YAMAS
        ObjectMapper om = new ObjectMapper();
        try{
            System.out.println(om.writeValueAsString(yamasSpecReport));
        }
        catch(JsonProcessingException e){
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        /**
         * When testing in docker container
         * docker run --hostname 13305821.ostk.bm2.prod.gq1.yahoo.com --name 13305821.ostk.bm2.prod.gq1.yahoo.com [image]
         */
        if (args.length != 1) {
            throw new RuntimeException("Expected only 1 argument - config server zone url");
        }
        String zoneHostName = args[0];
        SpecVerifier specVerifier = new SpecVerifier();
        specVerifier.verifySpec(zoneHostName);
    }

}
