// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.legacy;

/**
 * Value class used to automatically convert to/from JSON.
 *
 * @author Oyvind Gronnesby
 */
class HostInfo {

    public String hostname;
    public String openStackId;
    public String flavor;

    public static HostInfo createHostInfo(String hostname, String openStackId, String flavor) {
        HostInfo hostInfo = new HostInfo();
        hostInfo.hostname = hostname;
        hostInfo.openStackId = openStackId;
        hostInfo.flavor = flavor;
        return hostInfo;
    }

    public String toString(){
        return String.format("%s/%s", openStackId, hostname);
    }

}
