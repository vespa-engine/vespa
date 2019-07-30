package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.amazonaws.services.ec2.model.InstanceStatusEvent;

import java.util.Date;
import java.util.Set;

public class CloudEvent {
    public String instanceEventId;
    public String code;
    public String description;
    public Date notAfter;
    public Date notBefore;
    public Date notBeforeDeadline;

    public String zoneName;
    public Set<String> affectedHostnames;

    private CloudEvent() { }

    public static CloudEvent fromAwsEvent(InstanceStatusEvent event, String zoneName, Set<String> affectedHostnames) {
        CloudEvent cloudEvent = new CloudEvent();
        cloudEvent.instanceEventId = event.getInstanceEventId();
        cloudEvent.code = event.getCode();
        cloudEvent.description = event.getDescription();
        cloudEvent.notAfter = event.getNotAfter();
        cloudEvent.notBefore = event.getNotBefore();
        cloudEvent.notBeforeDeadline = event.getNotBeforeDeadline();

        cloudEvent.zoneName = zoneName;
        cloudEvent.affectedHostnames = affectedHostnames;

        return cloudEvent;
    }
}
