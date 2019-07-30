package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.amazonaws.regions.Regions;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Issue;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface AwsEventFetcher {
    List<CloudEvent> getEvents(Regions awsRegion);
    Issue createIssue(CloudEvent event);

    default Regions zoneToAwsRegion(ZoneId zoneId) {
        final Pattern AWS_REGION_PATTERN = Pattern.compile("^(?:cd-)?aws-(([a-z]+-[a-z]+-[0-9])[a-z])$");
        Matcher matcher = AWS_REGION_PATTERN.matcher(zoneId.region().value());
        if (!matcher.find())
            throw new IllegalArgumentException(String.format(
                    "Unable to determine Amazon availability zone, region '%s' does not match %s",
                    zoneId.region().value(), AWS_REGION_PATTERN.pattern()));
        return Regions.fromName(matcher.group(2));
    }

}
