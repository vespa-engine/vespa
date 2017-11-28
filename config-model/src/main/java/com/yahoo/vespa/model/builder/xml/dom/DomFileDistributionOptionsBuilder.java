// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.binaryprefix.BinaryScaledAmount;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.admin.FileDistributionOptions;
import org.w3c.dom.Element;

import java.util.Optional;

/**
 * Builds file distribution options.
 *
 * @author Tony Vaagenes
 * @author hmusum
 */
public class DomFileDistributionOptionsBuilder {
    private final FileDistributionOptions fileDistributionOptions;

    public DomFileDistributionOptionsBuilder(FileDistributionOptions fileDistributionOptions) {
        this.fileDistributionOptions = fileDistributionOptions;
    }

    private static void throwExceptionForElementInFileDistribution(String subElement, String reason) {
        throw new RuntimeException("In element '" + subElement + "' contained in 'filedistribution': " + reason);
    }

    private Optional<BinaryScaledAmount> getAmount(String name, Element fileDistributionElement) {
        Element optionElement = XML.getChild(fileDistributionElement, name);
        try {
            if (optionElement != null) {
                String valueString = XML.getValue(optionElement);
                return Optional.of(BinaryScaledAmountParser.parse(valueString));
            }
        } catch (NumberFormatException e) {
            throwExceptionForElementInFileDistribution(name, "Expected a valid number. (Message = " + e.getMessage() + ").");
        }
        return Optional.empty();
    }

    public FileDistributionOptions build(Element fileDistributionElement) {
        if (fileDistributionElement != null) {
            getAmount("uploadbitrate", fileDistributionElement).ifPresent(fileDistributionOptions::uploadBitRate);
            getAmount("downloadbitrate", fileDistributionElement).ifPresent(fileDistributionOptions::downloadBitRate);
            Element disable = XML.getChild(fileDistributionElement, "disabled");
            if (disable == null) disable = XML.getChild(fileDistributionElement, "disableFiledistributor");
            if (disable != null) {
                fileDistributionOptions.disableFiledistributor(Boolean.valueOf(XML.getValue(disable)));
            }
        }
        return fileDistributionOptions;
    }
}
