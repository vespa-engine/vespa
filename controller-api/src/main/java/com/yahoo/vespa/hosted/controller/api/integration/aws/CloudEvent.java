package com.yahoo.vespa.hosted.controller.api.integration.aws;

import java.util.Date;
import java.util.Optional;
import java.util.Set;

public class CloudEvent {
    public String instanceEventId;
    public String code;
    public String description;
    public Optional<Date> notBefore;
    public Optional<Date> notBeforeDeadline;
    public Optional<Date> notAfter;

    public String regionName;
    public Set<String> affectedHostnames;

    public CloudEvent(String instanceEventId, String code, String description, Date notAfter, Date notBefore, Date notBeforeDeadline,
                      String regionName, Set<String> affectedHostnames) {
        this.instanceEventId = instanceEventId;
        this.code = code;
        this.description = description;
        this.notBefore = Optional.ofNullable(notBefore);
        this.notBeforeDeadline = Optional.ofNullable(notBeforeDeadline);
        this.notAfter = Optional.ofNullable(notAfter);

        this.regionName = regionName;
        this.affectedHostnames = affectedHostnames;
    }
}
