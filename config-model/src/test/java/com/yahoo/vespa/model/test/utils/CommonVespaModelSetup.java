// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test.utils;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;

import java.io.File;

/**
 * @author Tony Vaagenes
 */
//TODO Remove, use VespaModelCreatorWithMockPkg or VespaModelCreatorWithFilePkg instead
public class CommonVespaModelSetup {

    public static VespaModel createVespaModelWithMusic(String path) {
        return createVespaModelWithMusic(new File(path));
    }

    public static VespaModel createVespaModelWithMusic(File dir) {
        VespaModelCreatorWithFilePkg modelCreator = new VespaModelCreatorWithFilePkg(dir);
        return modelCreator.create();
    }

    public static VespaModel createVespaModelWithMusic(String hosts, String services) {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withHosts(hosts)
                .withServices(services)
                .withSearchDefinition(MockApplicationPackage.MUSIC_SEARCHDEFINITION)
                .build();
        VespaModelCreatorWithMockPkg modelCreator = new VespaModelCreatorWithMockPkg(app);
        return modelCreator.create();
    }
}
