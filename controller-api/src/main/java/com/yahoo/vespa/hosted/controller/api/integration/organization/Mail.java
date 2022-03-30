package com.yahoo.vespa.hosted.controller.api.integration.organization;

import java.util.Collection;

public interface Mail {
    Collection<String> recipients();
}
