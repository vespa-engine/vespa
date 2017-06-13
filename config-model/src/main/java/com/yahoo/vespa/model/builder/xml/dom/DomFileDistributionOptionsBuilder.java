// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.binaryprefix.BinaryScaledAmount;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.admin.FileDistributionOptions;
import org.w3c.dom.Element;

/**
 * Builds a file distribution options.
 * @author tonytv
 */
public class DomFileDistributionOptionsBuilder {
    private static void throwExceptionForElementInFileDistribution(String subElement, String reason) {
        throw new RuntimeException("In element '" + subElement + "' contained in 'filedistribution': " + reason);
    }

    private static void callSetter(FileDistributionOptions options, String name, BinaryScaledAmount amount) {
        try {
            options.getClass().getMethod(name, BinaryScaledAmountParser.class).invoke(options, amount);
        } catch (IllegalArgumentException e) {
            throwExceptionForElementInFileDistribution(name, e.getMessage());
        }
        catch (Exception e) {
            if (e instanceof RuntimeException)
                throw (RuntimeException)e;
            else
                throw new RuntimeException(e);
        }
    }

    private static void setIfPresent(FileDistributionOptions options, String name, Element fileDistributionElement) {
        try {
            Element optionElement = XML.getChild(fileDistributionElement, name);
            if (optionElement != null) {
                String valueString = XML.getValue(optionElement);
                BinaryScaledAmount amount = BinaryScaledAmountParser.parse(valueString);
                callSetter(options, name, amount);
            }
        } catch (NumberFormatException e) {
            throwExceptionForElementInFileDistribution(name, "Expected a valid number. (Message = " + e.getMessage() + ").");
        }
    }

    public FileDistributionOptions build(Element fileDistributionElement) {
        FileDistributionOptions options = FileDistributionOptions.defaultOptions();
        if (fileDistributionElement != null) {
            setIfPresent(options, "uploadbitrate", fileDistributionElement);
            setIfPresent(options, "downloadbitrate", fileDistributionElement);
        }
        return options;
    }
}
