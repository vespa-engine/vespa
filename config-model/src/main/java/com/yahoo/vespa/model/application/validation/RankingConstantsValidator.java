// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.path.Path;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ConstantTensorJsonValidator.InvalidConstantTensor;
import com.yahoo.vespa.model.search.SearchDefinition;

import java.io.FileNotFoundException;

/**
 * RankingConstantsValidator validates all constant tensors (ranking constants) bundled with an application package
 *
 * @author Vegard Sjonfjell
 */
public class RankingConstantsValidator extends Validator {

    private static class ExceptionMessageCollector {

        String combinedMessage;
        boolean exceptionsOccurred = false;

        ExceptionMessageCollector(String messagePrelude) {
            this.combinedMessage = messagePrelude;
        }

        public ExceptionMessageCollector add(Throwable throwable, String rcName, String rcFilename) {
            exceptionsOccurred = true;
            combinedMessage += String.format("\nRanking constant \"%s\" (%s): %s", rcName, rcFilename, throwable.getMessage());
            return this;
        }
    }

    static class TensorValidationFailed extends RuntimeException {
        TensorValidationFailed(String message) {
            super(message);
        }
    }

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        ApplicationPackage applicationPackage = deployState.getApplicationPackage();
        ExceptionMessageCollector exceptionMessageCollector = new ExceptionMessageCollector("Invalid constant tensor file(s):");

        for (SearchDefinition sd : deployState.getSearchDefinitions()) {
            for (RankingConstant rc : sd.getSearch().getRankingConstants().values()) {
                try {
                    validateRankingConstant(rc, applicationPackage);
                } catch (InvalidConstantTensor | FileNotFoundException ex) {
                    exceptionMessageCollector.add(ex, rc.getName(), rc.getFileName());
                }
            }
        }

        if (exceptionMessageCollector.exceptionsOccurred) {
            throw new TensorValidationFailed(exceptionMessageCollector.combinedMessage);
        }
    }

    private void validateRankingConstant(RankingConstant rankingConstant, ApplicationPackage application) throws FileNotFoundException {
        // TODO: Handle validation of URI soon too.
        if (rankingConstant.getPathType() == RankingConstant.PathType.FILE) {
            String constantFile = rankingConstant.getFileName();
            if (application.getFileReference(Path.fromString("")).getAbsolutePath().endsWith(FilesApplicationPackage.preprocessed) &&
                    constantFile.startsWith(FilesApplicationPackage.preprocessed)) {
                constantFile = constantFile.substring(FilesApplicationPackage.preprocessed.length());
            }

            ApplicationFile tensorApplicationFile = application.getFile(Path.fromString(constantFile));
            new ConstantTensorJsonValidator().validate(constantFile,
                    rankingConstant.getTensorType(),
                    tensorApplicationFile.createReader());
        }
    }

}
