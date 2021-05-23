package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.utils.FileSender;

import java.util.Collection;

public class RankExpressionFile extends DistributableResource {

    public RankExpressionFile(String name, String path) {
        super(name, path);
        validate();
    }

    @Override
    public void sendTo(Collection<? extends AbstractService> services) {
        /*
         *  TODO This is a very dirty hack due to using both SEARCH_DEFINITIONS_DIR and SCHEMA_DIR
         *  and doing so inconsistently, combined with using both fields from application package on disk and in zookeeper.
         *  The mess is spread out nicely, but ZookeeperClient, and writeSearchDefinitions and ZkApplicationPackage and FilesApplicationPackage
         *  should be consolidated
        */
        try {
            setFileReference(FileSender.sendFileToServices(ApplicationPackage.SCHEMAS_DIR + "/" + getFileName(), services).value());
        } catch (IllegalArgumentException e1) {
            try {
                setFileReference(FileSender.sendFileToServices(ApplicationPackage.SEARCH_DEFINITIONS_DIR + "/" + getFileName(), services).value());
            } catch (IllegalArgumentException e2) {
                throw new IllegalArgumentException("Failed to find expression file '" + getFileName() + "' in '"
                        + ApplicationPackage.SEARCH_DEFINITIONS_DIR + "' or '" + ApplicationPackage.SCHEMAS_DIR + "'.", e2);
            }
        }
    }
}
