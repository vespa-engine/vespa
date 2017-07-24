package com.yahoo.vespa.hosted.node.verification.spec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.spec.hardware.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeInfoRetriever;
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
        HardwareInfo node = NodeInfoRetriever.retrieve(nodeRepoUrl);
        HardwareInfo actualHardware = HardwareInfoRetriever.retrieve();
        YamasSpecReport yamasSpecReport = HardwareNodeComparator.compare(node, actualHardware);
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
        String zoneHostName = "http://cfg1.prod.us-west-1.vespahosted.gq1.yahoo.com:4080";
        zoneHostName = "http://cfg1.perf.us-east-3.vespahosted.bf1.yahoo.com:4080";
        SpecVerifier specVerifier = new SpecVerifier();
        specVerifier.verifySpec(zoneHostName);
    }

}
