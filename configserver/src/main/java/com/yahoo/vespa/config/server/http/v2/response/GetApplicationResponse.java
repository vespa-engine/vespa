// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.component.Version;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.http.JSONResponse;

import java.util.List;
import java.util.Optional;

public class GetApplicationResponse extends JSONResponse {
    public GetApplicationResponse(int status, long generation, List<Version> modelVersions, Optional<String> applicationPackageReference) {
        super(status);
        object.setLong("generation", generation);
        object.setString("applicationPackageFileReference", applicationPackageReference.orElse(""));
        Cursor modelVersionArray = object.setArray("modelVersions");
        modelVersions.forEach(version -> modelVersionArray.addString(version.toFullString()));
    }
}
