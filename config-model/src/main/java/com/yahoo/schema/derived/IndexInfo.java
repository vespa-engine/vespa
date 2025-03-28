// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.schema.Index;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.BooleanIndexDefinition;
import com.yahoo.schema.document.Case;
import com.yahoo.schema.document.FieldSet;
import com.yahoo.schema.document.GeoPos;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.Matching;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.Stemming;
import com.yahoo.schema.processing.ExactMatch;
import com.yahoo.schema.processing.NGramMatch;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.search.config.IndexInfoConfig;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Per-index commands which should be applied to queries prior to searching
 *
 * @author bratseth
 */
public class IndexInfo extends Derived {

    private static final String CMD_ATTRIBUTE = "attribute";
    private static final String CMD_DEFAULT_POSITION = "default-position";
    private static final String CMD_DYNTEASER = "dynteaser";
    private static final String CMD_FULLURL = "fullurl";
    private static final String CMD_HIGHLIGHT = "highlight";
    private static final String CMD_INDEX = "index";
    private static final String CMD_LOWERCASE = "lowercase";
    private static final String CMD_NORMALIZE = "normalize";
    private static final String CMD_STEM = "stem";
    private static final String CMD_URLHOST = "urlhost";
    private static final String CMD_WORD = "word";
    private static final String CMD_PLAIN_TOKENS = "plain-tokens";
    private static final String CMD_MULTIVALUE = "multivalue";
    private static final String CMD_FAST_SEARCH = "fast-search";
    private static final String CMD_PREDICATE = "predicate";
    private static final String CMD_PREDICATE_BOUNDS = "predicate-bounds";
    private static final String CMD_NUMERICAL = "numerical";
    private static final String CMD_INTEGER = "integer";
    private static final String CMD_STRING = "string";
    private static final String CMD_PHRASE_SEGMENTING = "phrase-segmenting";
    private final boolean isStreaming;
    private final Set<IndexCommand> commands = new java.util.LinkedHashSet<>();
    private final Map<String, String> aliases = new java.util.LinkedHashMap<>();
    private final Map<String, FieldSet> fieldSets;
    private Schema schema;

    public IndexInfo(Schema schema, boolean isStreaming) {
        this.isStreaming = isStreaming;
        this.fieldSets = schema.fieldSets().userFieldSets();
        addIndexCommand("sddocname", CMD_INDEX);
        addIndexCommand("sddocname", CMD_WORD);
        derive(schema);
    }

    @Override
    protected void derive(Schema schema) {
        super.derive(schema); // Derive per field
        this.schema = schema;
        // Populate fieldsets with actual field objects, bit late to do that here but
        for (FieldSet fs : fieldSets.values()) {
            for (String fieldName : fs.getFieldNames()) {
                fs.fields().add(schema.getField(fieldName));
            }
        }
        // Must follow, because index settings overrides field settings
        for (Index index : schema.getExplicitIndices()) {
            derive(index, schema);
        }

        // Commands for summary fields
        // TODO: Move to schemainfo and implement differently
        for (SummaryField summaryField : schema.getUniqueNamedSummaryFields().values()) {
            if (summaryField.getTransform().isTeaser()) {
                addIndexCommand(summaryField.getName(), CMD_DYNTEASER);
            }
            if (summaryField.getTransform().isBolded()) {
                addIndexCommand(summaryField.getName(), CMD_HIGHLIGHT);
            }

            var sourceField = schema.getField(summaryField.getSourceField()); // Take the first as they should all be consistent
            if (sourceField != null && sourceField.getMatching().getType().equals(MatchType.GRAM)) {
                addIndexCommand(summaryField.getName(),
                                "ngram " + (sourceField.getMatching().getGramSize().orElse(NGramMatch.DEFAULT_GRAM_SIZE)));

            }
        }
    }

    private static boolean isPositionField(ImmutableSDField field) {
        return (field != null) && GeoPos.isAnyPos(field);
    }
    private static boolean isMultivalueField(ImmutableSDField field) {
        return (field != null) && field.getDataType().isMultivalue();
    }

    @Override
    protected void derive(ImmutableSDField field, Schema schema) {
        derive(field, schema, null);
    }

    protected void derive(ImmutableSDField field, Schema schema, ImmutableSDField parent) {
        if (field.getDataType().equals(DataType.PREDICATE)) {
            addIndexCommand(field, CMD_PREDICATE);
            Index index = field.getIndex(field.getName());
            if (index != null) {
                BooleanIndexDefinition options = index.getBooleanIndexDefiniton();
                if (options.hasLowerBound() || options.hasUpperBound()) {
                    addIndexCommand(field.getName(), CMD_PREDICATE_BOUNDS + " [" +
                            (options.hasLowerBound() ? Long.toString(options.getLowerBound()) : "") + ".." +
                            (options.hasUpperBound() ? Long.toString(options.getUpperBound()) : "") + "]");
                }
            }
        }

        // Field level aliases
        for (Map.Entry<String, String> e : field.getAliasToName().entrySet()) {
            String alias = e.getKey();
            String name = e.getValue();
            addIndexAlias(alias, name);
        }
        if (field.usesStructOrMap()) {
            for (ImmutableSDField structField : field.getStructFields()) {
                derive(structField, schema, field); // Recursion
            }
        }

        if (isPositionField(field)) {
            addIndexCommand(field.getName(), CMD_DEFAULT_POSITION);
        }

        for (var index : field.getIndices().values()) {
            addIndexCommand(index.getName(), CMD_INDEX); // List the indices
        }

        if (needLowerCase(field)) {
            addIndexCommand(field, CMD_LOWERCASE);
        }

        if (isMultivalueField(field) || isMultivalueField(parent)) {
            addIndexCommand(field, CMD_MULTIVALUE);
        }

        Attribute attribute = field.getAttribute();
        if ((field.doesAttributing() || (attribute != null && !isPositionField(parent))) && !field.doesIndexing()) {
            addIndexCommand(field.getName(), CMD_ATTRIBUTE);
            if (attribute != null && attribute.isFastSearch())
                addIndexCommand(field.getName(), CMD_FAST_SEARCH);
        } else if (field.doesIndexing()) {
            if (stemSomehow(field, schema)) {
                addIndexCommand(field, stemCmd(field, schema), new StemmingOverrider(this, schema));
            }
            if (normalizeAccents(field)) {
                addIndexCommand(field, CMD_NORMALIZE);
            }
            if (field.getMatching() == null || field.getMatching().getType().equals(MatchType.TEXT)) {
                addIndexCommand(field, CMD_PLAIN_TOKENS);
            }
        }

        if (isUriField(field)) {
            addUriIndexCommands(field);
        }
        if (field.getDataType().getPrimitiveType() instanceof NumericDataType) {
            addIndexCommand(field, CMD_NUMERICAL);
            if (isTypeOrNested(field, DataType.INT) || isTypeOrNested(field, DataType.LONG) ||
                    isTypeOrNested(field, DataType.BYTE)) {
                addIndexCommand(field, CMD_INTEGER);
            }
        }
        if (isTypeOrNested(field, DataType.STRING)) {
            addIndexCommand(field, CMD_STRING);
        }

        // Explicit commands
        for (String command : field.getQueryCommands()) {
            addIndexCommand(field, command);
        }

    }

    private static boolean isAnyChildString(DataType dataType) {
        PrimitiveDataType primitive = dataType.getPrimitiveType();
        if (primitive == PrimitiveDataType.STRING) return true;
        if (primitive != null) return false;
        if (dataType instanceof StructuredDataType structured) {
            for (Field field : structured.getFields()) {
                if (isAnyChildString(field.getDataType())) return true;
            }
        } else if (dataType instanceof MapDataType mapType) {
            return isAnyChildString(mapType.getKeyType()) || isAnyChildString(mapType.getValueType());
        }
        return false;
    }

    private static boolean needLowerCase(ImmutableSDField field) {
        return ( field.doesIndexing() && field.getMatching().getCase() != Case.CASED) ||
               field.doesLowerCasing() ||
               ((field.doesAttributing() || (field.getAttribute() != null)) &&
                isAnyChildString(field.getDataType())
                && field.getMatching().getCase().equals(Case.UNCASED));
    }

    static String stemCmd(ImmutableSDField field, Schema schema) {
        return CMD_STEM + ":" + field.getStemming(schema).toStemMode();
    }

    private boolean stemSomehow(ImmutableSDField field, Schema schema) {
        if (field.getStemming(schema).equals(Stemming.NONE)) return false;
        return isTypeOrNested(field, DataType.STRING);
    }

    private boolean normalizeAccents(ImmutableSDField field) {
        return !isStreaming && field.getNormalizing().doRemoveAccents() && isTypeOrNested(field, DataType.STRING);
    }

    private boolean isTypeOrNested(ImmutableSDField field, DataType type) {
        return field.getDataType().equals(type) || field.getDataType().equals(DataType.getArray(type)) ||
               field.getDataType().equals(DataType.getWeightedSet(type));
    }

    private boolean isUriField(ImmutableSDField field) {
        DataType fieldType = field.getDataType();
        if (DataType.URI.equals(fieldType)) {
            return true;
        }
        return (fieldType instanceof CollectionDataType collectionFieldType) &&
                DataType.URI.equals(collectionFieldType.getNestedType());
    }

    private void addUriIndexCommands(ImmutableSDField field) {
        String fieldName = field.getName();
        addIndexCommand(fieldName, CMD_FULLURL);
        addIndexCommand(fieldName, CMD_LOWERCASE);
        addIndexCommand(fieldName + "." + fieldName, CMD_FULLURL);
        addIndexCommand(fieldName + "." + fieldName, CMD_LOWERCASE);
        addIndexCommand(fieldName + ".path", CMD_FULLURL);
        addIndexCommand(fieldName + ".path", CMD_LOWERCASE);
        addIndexCommand(fieldName + ".query", CMD_FULLURL);
        addIndexCommand(fieldName + ".query", CMD_LOWERCASE);
        addIndexCommand(fieldName + ".hostname", CMD_URLHOST);
        addIndexCommand(fieldName + ".hostname", CMD_LOWERCASE);

        // XXX hack
        Index index = field.getIndex("hostname");
        if (index != null) {
            addIndexCommand(index, CMD_URLHOST);
        }
    }

    /**
     * Sets a command for all indices of a field
     */
    private void addIndexCommand(Index index, String command) {
        addIndexCommand(index.getName(), command);
    }

    /**
     * Sets a command for all indices of a field
     */
    private void addIndexCommand(ImmutableSDField field, String command) {
        addIndexCommand(field, command, null);
    }

    /**
     * Sets a command for all indices of a field
     */
    private void addIndexCommand(ImmutableSDField field, String command, IndexOverrider overrider) {
        if (overrider == null || !overrider.override(field.getName(), command, field)) {
            addIndexCommand(field.getName(), command);
        }
    }

    private void addIndexCommand(String indexName, String command) {
        commands.add(new IndexCommand(indexName, command));
    }

    private static void addIndexCommand(IndexInfoConfig.Indexinfo.Builder iiB, String indexName, String command) {
        iiB.command(new IndexInfoConfig.Indexinfo.Command.Builder().indexname(indexName).command(command));
    }

    private void addIndexAlias(String alias, String indexName) {
        aliases.put(alias, indexName);
    }

    /**
     * Returns whether a particular command is prsent in this index info
     */
    public boolean hasCommand(String indexName, String command) {
        return commands.contains(new IndexCommand(indexName, command));
    }

    private boolean notInCommands(String index) {
        for (IndexCommand command : commands) {
            if (command.index().equals(index)) {
                return false;
            }
        }
        return true;
    }

    public void getConfig(IndexInfoConfig.Builder builder) {
        // Append
        IndexInfoConfig.Indexinfo.Builder iiB = new IndexInfoConfig.Indexinfo.Builder();
        iiB.name(getName());
        for (IndexCommand command : commands) {
            addIndexCommand(iiB, command.index(), command.command());
        }
        // Make user defined field sets searchable
        for (FieldSet fieldSet : fieldSets.values()) {
        	 if (notInCommands(fieldSet.getName())) {
        		 addFieldSetCommands(iiB, fieldSet);
        	 }
        }

        for (Map.Entry<String, String> e : aliases.entrySet()) {
            iiB.alias(new IndexInfoConfig.Indexinfo.Alias.Builder().alias(e.getKey()).indexname(e.getValue()));
        }
        builder.indexinfo(iiB);
    }

    public void export(String toDirectory) throws IOException {
        var builder = new IndexInfoConfig.Builder();
        getConfig(builder);
        export(toDirectory, builder.build());
    }

    // TODO: Move this to the FieldSetSettings processor (and rename it) as that already has to look at this.
    private void addFieldSetCommands(IndexInfoConfig.Indexinfo.Builder iiB, FieldSet fieldSet) {
        for (String qc : fieldSet.queryCommands()) {
            addIndexCommand(iiB, fieldSet.getName(), qc);
        }
        boolean anyIndexing = false;
        boolean anyAttributing = false;
        boolean anyLowerCasing = false;
        boolean anyStemming = false;
        boolean anyNormalizing = false;
        boolean anyString = false;
        boolean anyInteger = false;
        String phraseSegmentingCommand = null;
        String stemmingCommand = null;
        Matching fieldSetMatching = fieldSet.getMatching(); // null if no explicit matching
        // First a pass over the fields to read some params to decide field settings implicitly:
        for (ImmutableSDField field : fieldSet.fields()) {
            if (field.doesIndexing()) {
                anyIndexing = true;
            }
            if (field.doesAttributing()) {
                anyAttributing = true;
            }
            if (needLowerCase(field)) {
                anyLowerCasing = true;
            }
            if (stemming(field)) {
                anyStemming = true;
                stemmingCommand = CMD_STEM + ":" + getEffectiveStemming(field).toStemMode();
            }
            if (normalizeAccents(field)) {
                anyNormalizing = true;
            }
            if (isTypeOrNested(field, DataType.STRING)) {
                anyString = true;
            }
            if (fieldSetMatching == null && field.getMatching().getType() != Matching.defaultType) {
                fieldSetMatching = field.getMatching();
            }
            Optional<String> explicitPhraseSegmentingCommand = field.getQueryCommands().stream().filter(c -> c.startsWith(CMD_PHRASE_SEGMENTING)).findFirst();
            if (explicitPhraseSegmentingCommand.isPresent()) {
                phraseSegmentingCommand = explicitPhraseSegmentingCommand.get();
            }
            if (isTypeOrNested(field, DataType.INT) || isTypeOrNested(field, DataType.LONG) ||
                    isTypeOrNested(field, DataType.BYTE)) {
                anyInteger = true;
            }
        }
        if (anyIndexing && anyAttributing && fieldSet.getMatching() == null) {
            // We have both attributes and indexes and no explicit match setting ->
            // use default matching as that at least works if the data in the attribute consists
            // of single tokens only.
            fieldSetMatching = new Matching();
        }
        if (anyLowerCasing) {
            addIndexCommand(iiB, fieldSet.getName(), CMD_LOWERCASE);
        }
        if (hasMultiValueField(fieldSet)) {
            addIndexCommand(iiB, fieldSet.getName(), CMD_MULTIVALUE);
        }
        if (anyIndexing) {
            addIndexCommand(iiB, fieldSet.getName(), CMD_INDEX);
            if ( ! isExactMatch(fieldSetMatching)) {
                if (fieldSetMatching == null || fieldSetMatching.getType().equals(MatchType.TEXT)) {
                    addIndexCommand(iiB, fieldSet.getName(), CMD_PLAIN_TOKENS);
                }
                if (anyStemming) {
                    addIndexCommand(iiB, fieldSet.getName(), stemmingCommand);
                }
                if (anyNormalizing)
                    addIndexCommand(iiB, fieldSet.getName(), CMD_NORMALIZE);
                if (phraseSegmentingCommand != null)
                    addIndexCommand(iiB, fieldSet.getName(), phraseSegmentingCommand);
            }
        } else {
            // Assume only attribute fields
            addIndexCommand(iiB, fieldSet.getName(), CMD_ATTRIBUTE);
            addIndexCommand(iiB, fieldSet.getName(), CMD_INDEX);
        }
        if (anyString) {
            addIndexCommand(iiB, fieldSet.getName(), CMD_STRING);
        }
        if (anyInteger) {
            addIndexCommand(iiB, fieldSet.getName(), CMD_INTEGER);
        }
        if (fieldSetMatching != null) {
            // Explicit matching set on fieldset
            if (fieldSetMatching.getType().equals(MatchType.EXACT)) {
                String term = fieldSetMatching.getExactMatchTerminator();
                if (term==null) term=ExactMatch.DEFAULT_EXACT_TERMINATOR;
                addIndexCommand(iiB, fieldSet.getName(), "exact "+term);
            } else if (fieldSetMatching.getType().equals(MatchType.WORD)) {
                addIndexCommand(iiB, fieldSet.getName(), CMD_WORD);
            } else if (fieldSetMatching.getType().equals(MatchType.GRAM)) {
                addIndexCommand(iiB, fieldSet.getName(), "ngram " + fieldSetMatching.getGramSize().orElse(NGramMatch.DEFAULT_GRAM_SIZE));
            } else if (fieldSetMatching.getType().equals(MatchType.TEXT)) {
                
            }
        }
    }

    private boolean hasMultiValueField(FieldSet fieldSet) {
        for (ImmutableSDField field : fieldSet.fields()) {
            if (field.getDataType().isMultivalue())
                return true;
        }
        return false;
    }

    private Stemming getEffectiveStemming(ImmutableSDField field) {
        Stemming active = field.getStemming(schema);
        if (field.getIndex(field.getName()) != null) {
            if (field.getIndex(field.getName()).getStemming()!=null) {
                active = field.getIndex(field.getName()).getStemming();
            }
        }
        return Objects.requireNonNullElse(active, Stemming.BEST);
    }

    private boolean stemming(ImmutableSDField field) {
        if (field.getStemming() != null) {
            return !field.getStemming().equals(Stemming.NONE);
        }
        if (schema.getStemming() == Stemming.NONE) return false;
        if (field.isImportedField()) return false;
        if (field.getIndex(field.getName())==null) return true;
        if (field.getIndex(field.getName()).getStemming()==null) return true;
        return !(field.getIndex(field.getName()).getStemming().equals(Stemming.NONE));
    }

    private boolean isExactMatch(Matching m) {
        if (m == null) return false;
        return m.getType().equals(MatchType.EXACT) || m.getType().equals(MatchType.WORD);
    }

    @Override
    protected String getDerivedName() {
        return "index-info";
    }

    /**
     * An index command. Null commands are also represented, to detect consistency issues. This is an (immutable) value
     * object.
     */
    public record IndexCommand(String index, String command) {

        /**
         * Returns true if this is the null command (do nothing)
         */
        public boolean isNull() {
            return command.isEmpty();
        }

        public boolean equals(Object object) {
            if (!(object instanceof IndexCommand other)) {
                return false;
            }

            return other.index.equals(this.index) &&
                    other.command.equals(this.command);
        }

        public String toString() {
            return "index command " + command + " on index " + index;
        }

    }

    /**
     * A command which may override the command setting of a field for a particular index
     */
    private static abstract class IndexOverrider {

        protected final IndexInfo owner;

        public IndexOverrider(IndexInfo owner) {
            this.owner = owner;
        }

        /**
         * Override the setting of this index for this field, returns true if overriden, false if this index should be
         * set according to the field
         */
        public abstract boolean override(String indexName, String command, ImmutableSDField field);

    }

    private static class StemmingOverrider extends IndexOverrider {

        private final Schema schema;

        public StemmingOverrider(IndexInfo owner, Schema schema) {
            super(owner);
            this.schema = schema;
        }

        public boolean override(String indexName, String command, ImmutableSDField field) {
            if (schema == null) {
                return false;
            }

            Index index = schema.getIndex(indexName);
            if (index == null) {
                return false;
            }

            Stemming indexStemming = index.getStemming();
            if (indexStemming == null) {
                return false;
            }

            if ( ! Stemming.NONE.equals(indexStemming)) {
                owner.addIndexCommand(indexName, CMD_STEM + ":" + indexStemming.toStemMode());
            }
            return true;
        }

    }

}
