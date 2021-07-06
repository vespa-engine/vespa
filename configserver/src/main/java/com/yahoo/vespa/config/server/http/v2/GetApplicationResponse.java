package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.component.Version;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.http.JSONResponse;

import java.util.List;
import java.util.Optional;

class GetApplicationResponse extends JSONResponse {
    GetApplicationResponse(int status, long generation, List<Version> modelVersions, Optional<String> applicationPackageReference) {
        super(status);
        object.setLong("generation", generation);
        object.setString("applicationPackageFileReference", applicationPackageReference.orElse(""));
        Cursor modelVersionArray = object.setArray("modelVersions");
        modelVersions.forEach(version -> modelVersionArray.addString(version.toFullString()));
    }
}
