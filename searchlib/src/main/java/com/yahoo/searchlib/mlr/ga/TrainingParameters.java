// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import java.util.HashSet;
import java.util.Set;

/**
 * @author bratseth
 */
public class TrainingParameters {

    // A note:
    // The total number of species generated and evaluated is
    // (generationCandidatesFactor * speciesLifespan * (initialSpeciesSize-finalSpeciesSize)/2 ) ^ speciesLevels
    // (speciesLevel is hardcoded to 2 atm)

    private int speciesLifespan = 1000;
    private int initialSpeciesSize = 10;
    private double finalSpeciesSize = 1;
    private int generationCandidatesFactor = 3;
    private int maxExpressionDepth = 6;
    private boolean allowConditions = true;
    private boolean errorIsRelative = true;
    private Set<String> excludeFeatures = new HashSet<>();
    private String trainingSetFormat = null;
    private double validationFraction = 0.2;

    /** The number of generation which a given species (or super-species at any level) lives. Default:1000 */
    public int getSpeciesLifespan() { return speciesLifespan; }
    public void setSpeciesLifespan(int generations) { this.speciesLifespan = generations; }

    /** The number of members in a species (or super-species at any level) as it is created. Default: 10 */
    public int getInitialSpeciesSize() { return initialSpeciesSize; }
    public void setInitialSpeciesSize(int initialSpeciesSize) { this.initialSpeciesSize = initialSpeciesSize; }

    /**
     * The number of members in a species in its final generation.
     * The size of the species will be reduced linearly in each generation to go from initial size to final size.
     * Default: 1
     */
    public double getFinalSpeciesSize() { return finalSpeciesSize; }
    public void setFinalSpeciesSize(int finalSpeciesSize) { this.finalSpeciesSize = finalSpeciesSize; }

    /*
     * The factor determining how many more members are generated than are allowed to survive in each generation of a species.
     * Default: 3
     */
    public int getGenerationCandidatesFactor() { return generationCandidatesFactor; }
    public void setGenerationCandidatesFactor(int generationCandidatesFactor) { this.generationCandidatesFactor = generationCandidatesFactor; }

    /**
     * The max depth of expressions this is allowed to generate.
     * Default: 6
     */
    public int getMaxExpressionDepth() { return maxExpressionDepth; }
    public void setMaxExpressionDepth(int maxExpressionDepth) { this.maxExpressionDepth = maxExpressionDepth; }

    /**
     * Whether mutation should allow creation of condition (if) expressions.
     * Default: true
     */
    public boolean getAllowConditions() { return allowConditions; }
    public void setAllowConditions(boolean allowConditions) { this.allowConditions = allowConditions; }

    /**
     * Whether errors are relative to the absolute value of the function at that point or not.
     * If true, training will assign equal weight to the error of 1.1 for 1 and 110 for 100.
     * If false, training will instead assign a 10x weight to the latter.
     * Default: True.
     */
    public boolean getErrorIsRelative() { return errorIsRelative; }
    public void setErrorIsRelative(boolean errorIsRelative) { this.errorIsRelative = errorIsRelative; }

    /**
     * Returns the set of features to exclude during training.
     * Returned as an immutable set, never null.
     */
    public Set<String> getExcludeFeatures() { return excludeFeatures; }
    /** Sets the features to exclude from a comma-separated string */
    public void setExcludeFeatures(String excludeFeatureString) {
        for (String featureName : excludeFeatureString.split(","))
            excludeFeatures.add(featureName.trim());
    }

    /**
     * Returns the format of the training set to read. "fv" or "cvs" is supported.
     * If this is null the format name is taken from the last name of the file instead.
     * Default: null.
     */
    public String getTrainingSetFormat() { return trainingSetFormat; }
    public void setTrainingSetFormat(String trainingSetFormat) { this.trainingSetFormat = trainingSetFormat; }

    /**
     * Returns the fraction of the result set to hold out of training and use for validation.
     * Default 0.2
     */
    public double getValidationFraction() { return validationFraction; }
    public void setValidationFraction(double validationFraction) { this.validationFraction = validationFraction; }

}
