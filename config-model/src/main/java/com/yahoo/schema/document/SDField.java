// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.documentmodel.OwnedTemporaryType;
import com.yahoo.documentmodel.TemporaryUnknownType;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.schema.Index;
import com.yahoo.schema.Schema;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.indexinglanguage.ExpressionSearcher;
import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.IndexExpression;
import com.yahoo.vespa.indexinglanguage.expressions.LowerCaseExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SummaryExpression;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * The field class represents a document field. It is used in
 * the Document class to get and set fields. Each SDField has a name, a numeric ID,
 * a data type. The numeric ID is used when the fields are stored
 * in serialized form.
 *
 * @author bratseth
 */
public class SDField extends Field implements TypedKey, ImmutableSDField {

    /** Use this field for modifying index-structure, even if it doesn't have any indexing code */
    private boolean indexStructureField = false;

    /** The indexing statements to be applied to this value during indexing */
    private ScriptExpression indexingScript = new ScriptExpression();

    /** The default rank type for indices of this field */
    private RankType rankType = RankType.DEFAULT;

    /** Rank settings in a "rank" block for the field. */
    private final Ranking ranking = new Ranking();

    /**
     * The literal boost of this field. This boost is added to a rank score
     * when a query term matched as query term exactly (unnormalized and unstemmed).
     * Non-positive boosts causes no boosting, 0 allows boosts
     * to be specified in other rank profiles, while negative values
     * turns the capability off.
     */
    private int literalBoost = -1;

    /** 
     * The weight of this field. This is a percentage,
     * so 100 is default to provide the identity transform. 
     */
    private int weight = 100;

    /**
     * Indicates what kind of matching should be done on this field
     */
    private Matching matching = new Matching();

    private Dictionary dictionary = null;

    /** Attribute settings, or null if there are none */
    private final Map<String, Attribute> attributes = new TreeMap<>();

    /**
     * The stemming setting of this field, or null to use the default.
     * Default is determined by the owning search definition.
     */
    private Stemming stemming = null;

    /** How content of this field should be accent normalized etc. */
    private NormalizeLevel normalizing = new NormalizeLevel();

    /** Extra query commands of this field */
    private final List<String> queryCommands = new java.util.ArrayList<>(0);

    /** Summary fields defined in this field */
    private final Map<String, SummaryField> summaryFields = new java.util.LinkedHashMap<>(0);

    /** The explicitly index settings on this field */
    private final Map<String, Index> indices = new java.util.LinkedHashMap<>();

    private boolean idOverride = false;

    /** Struct fields defined in this field */
    private final Map<String,SDField> structFields = new java.util.LinkedHashMap<>(0);

    /** The document that this field was declared in, or null */
    private final SDDocumentType repoDocType;

    /** The aliases declared for this field. May pertain to indexes or attributes */
    private final Map<String, String> aliasToName = new HashMap<>();

    private boolean isExtraField = false;

    private boolean wasConfiguredToDoAttributing = false;
    private boolean wasConfiguredToDoIndexing = false;

    /**
     * Creates a new field. This method is only used to create reserved fields.
     *
     * @param name the name of the field
     * @param dataType the datatype of the field
    */
    public SDField(SDDocumentType repo, String name, int id, DataType dataType) {
        super(name, id, dataType);
        this.repoDocType = repo;
        populate(name, dataType);
    }

    public SDField(String name, DataType dataType) {
        this(null, name, dataType);
    }

    /** Creates a new field */
    public SDField(SDDocumentType repo, String name, DataType dataType) {
        this(repo, name, dataType, null);
    }

    /** Creates a new field */
    protected SDField(SDDocumentType repo, String name, DataType dataType, SDDocumentType owner) {
        this(repo, name, dataType, owner, null, 0);
    }

    /**
     * Creates a new field
     *
     * @param name the name of the field
     * @param dataType the datatype of the field
     * @param owner the owning document (used to check for id collisions)
     * @param fieldMatching the matching object to set for the field
     */
    protected SDField(SDDocumentType repo,
                      String name,
                      DataType dataType,
                      SDDocumentType owner,
                      Matching fieldMatching,
                      int recursion)
    {
        super(name, dataType, owner == null ? null : owner.getDocumentType());
        this.repoDocType = repo;
        this.structFieldDepth = recursion;
        if (fieldMatching != null)
            this.setMatching(fieldMatching);
        populate(name, dataType);
    }

    private int structFieldDepth = 0;

    private void populate(String name, DataType dataType) {
        if (dataType instanceof TensorDataType) {
            TensorType type = ((TensorDataType)dataType).getTensorType();
            if (type.dimensions().stream().anyMatch(d -> d.isIndexed() && d.size().isEmpty()))
                throw new IllegalArgumentException("Illegal type in field " + name + " type " + type +
                                                   ": Dense tensor dimensions must have a size");
            addQueryCommand("type " + type);
        }
        else if (dataType instanceof WeightedSetDataType) {
            var nested = ((WeightedSetDataType) dataType).getNestedType().getName();
            addQueryCommand("type WeightedSet<" + nested + ">");
        }
        else {
            addQueryCommand("type " + dataType.getName());
        }
    }

    public void setIsExtraField(boolean isExtra) {
        isExtraField = isExtra;
    }

    @Override
    public boolean isExtraField() {
        return isExtraField;
    }

    @Override
    public boolean isImportedField() {
        return false;
    }

    @Override
    public boolean doesAttributing() {
        return containsExpression(AttributeExpression.class);
    }

    @Override
    public boolean doesIndexing() {
        return containsExpression(IndexExpression.class);
    }

    public boolean doesSummarying() {
        if (usesStruct()) {
            for (SDField structField : getStructFields()) {
                if (structField.doesSummarying()) {
                    return true;
                }
            }
        }
        return containsExpression(SummaryExpression.class);
    }

    @Override
    public boolean doesLowerCasing() {
        return containsExpression(LowerCaseExpression.class);
    }

    @Override
    public <T extends Expression> boolean containsExpression(Class<T> searchFor) {
        return findExpression(searchFor) != null;
    }

    private <T extends Expression> T findExpression(Class<T> searchFor) {
        return new ExpressionSearcher<>(searchFor).searchIn(indexingScript);
    }

    public void addSummaryFieldSources(SummaryField summaryField) {
        if (usesStruct()) {
            /*
             * How this works for structs: When at least one sub-field in a struct is to
             * be used for summary, that whole struct field is included in summary.cfg. Then,
             * vsmsummary.cfg specifies the sub-fields used for each struct field.
             * So we recurse into each struct, adding the destination classes set for each sub-field
             * to the main summary-field for the struct field.
             */
            for (SDField structField : getStructFields()) {
                for (SummaryField sumF : structField.getSummaryFields().values()) {
                    for (String dest : sumF.getDestinations()) {
                        summaryField.addDestination(dest);
                    }
                }
                structField.addSummaryFieldSources(summaryField);
            }
        } else {
            if (doesSummarying()) {
                summaryField.addSource(getName());
            }
        }
    }

    private boolean doneStructFields = false;

    private void actuallyMakeStructFields() {
        if (doneStructFields) return;
        if (getFirstStructOrMapRecursive() == null) {
            doneStructFields = true;
            return;
        }
        var sdoc = repoDocType;
        var dataType = getDataType();

        java.util.function.BiConsumer<String, DataType> supplyStructField = (fieldName, fieldType) -> {
            if (structFields.containsKey(fieldName)) return;
            Matching subFieldMatching = new Matching();
            subFieldMatching.merge(this.matching);
            String subName = getName().concat(".").concat(fieldName);
            var subField = new SDField(sdoc, subName, fieldType, null,
                                       subFieldMatching, structFieldDepth + 1);
            structFields.put(fieldName, subField);
        };

        if (dataType instanceof MapDataType mdt) {
            supplyStructField.accept("key", mdt.getKeyType());
            supplyStructField.accept("value", mdt.getValueType());
        } else {
            if (structFieldDepth >= 10) {
                // too risky, infinite recursion
                doneStructFields = true;
                return;
            }
            if (dataType instanceof CollectionDataType) {
                dataType = ((CollectionDataType)dataType).getNestedType();
            }
            if ((dataType instanceof MapDataType) || (dataType instanceof CollectionDataType)) {
                // "array of map" or "array of array" will not have any struct fields
                // TODO: consider what this would mean
                doneStructFields = true;
                return;
            }
            SDDocumentType subType = sdoc != null ? sdoc.getType(dataType.getName()) : null;
            if (dataType instanceof TemporaryUnknownType && subType != null) {
                for (Field field : subType.fieldSet()) {
                    supplyStructField.accept(field.getName(), field.getDataType());
                }
            } else if (dataType instanceof OwnedTemporaryType && subType != null) {
                for (Field field : subType.fieldSet()) {
                    supplyStructField.accept(field.getName(), field.getDataType());
                }
            } else if (dataType instanceof StructDataType sdt) {
                for (Field field : sdt.getFields()) {
                    supplyStructField.accept(field.getName(), field.getDataType());
                }
            }
            if ((subType == null) && (structFields.size() > 0)) {
                throw new IllegalArgumentException("Cannot find matching (repo=" + sdoc + ") for subfields in "
                                                   + this + " [" + getDataType() + getDataType().getClass() +
                                                   "] with " + structFields.size() + " struct fields");
            }
            // populate struct fields with matching
            if (subType != null) {
                for (Field f : subType.fieldSet()) {
                    if (f instanceof SDField sdField) {
                        SDField subField = structFields.get(sdField.getName());
                        if (subField != null) {
                            // we just made this with a copy of our matching (see above)
                            Matching subFieldMatching = subField.getMatching();
                            subFieldMatching.merge(sdField.getMatching());
                            subField.setMatching(subFieldMatching);
                        }
                    } else {
                        throw new IllegalArgumentException("Field in struct is not SDField " + f.getName());
                    }
                }
            }
            // else ("missing subtype for struct fields in: " + this + " type " + getDataType() + " [" + getDataType().getClass().getSimpleName() + "]");
        }
        doneStructFields = true;
    }

    public void setId(int fieldId, DocumentType owner) {
        super.setId(fieldId, owner);
        idOverride = true;
    }

    public StructDataType getFirstStructRecursive() {
        DataType dataType = getDataType();
        while (true) { // Currently no nesting of collections
            if (dataType instanceof CollectionDataType) {
                dataType = ((CollectionDataType)dataType).getNestedType();
            } else if (dataType instanceof MapDataType) {
                dataType = ((MapDataType)dataType).getValueType();
            } else {
                break;
            }
        }
        return (dataType instanceof StructDataType) ? (StructDataType)dataType : null;
    }

    private DataType getFirstStructOrMapRecursive() {
        DataType dataType = getDataType();
        while (dataType instanceof CollectionDataType) { // Currently no nesting of collections
            dataType = ((CollectionDataType)dataType).getNestedType();
        }
        return (dataType instanceof StructDataType || dataType instanceof MapDataType) ? dataType : null;
    }

    private boolean usesStruct() {
        DataType dt = getFirstStructRecursive();
        return (dt != null);
    }

    @Override
    public boolean usesStructOrMap() {
        DataType dt = getFirstStructOrMapRecursive();
        return (dt != null);
    }

    @Override
    public boolean wasConfiguredToDoAttributing() {
        return wasConfiguredToDoAttributing;
    }

    @Override
    public boolean wasConfiguredToDoIndexing() {
        return wasConfiguredToDoIndexing;
    }

    @Override
    public boolean hasSingleAttribute() {
        if (getAttributes().size() != 1) {
            return false;
        }
        return (getAttributes().get(getName()) != null);
    }

    /** Parse an indexing expression which will use the simple linguistics implementation suitable for testing */
    public void parseIndexingScript(String script) {
        parseIndexingScript(script, new SimpleLinguistics(), Embedder.throwsOnUse.asMap());
    }

    public void parseIndexingScript(String script, Linguistics linguistics, Map<String, Embedder> embedders) {
        try {
            ScriptParserContext config = new ScriptParserContext(linguistics, embedders);
            config.setInputStream(new IndexingInput(script));
            setIndexingScript(ScriptExpression.newInstance(config));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Failed to parse script '" + script + "'", e);
        }
    }

    /** Sets the indexing script of this, or null to not use a script */
    public void setIndexingScript(ScriptExpression exp) {
        if (exp == null) {
            exp = new ScriptExpression();
        }
        indexingScript = exp;
        if (indexingScript.isEmpty()) {
            return; // TODO: This causes empty expressions not to be propagate to struct fields!! BAD BAD BAD!!
        }
        if (!wasConfiguredToDoAttributing()) {
            wasConfiguredToDoAttributing = doesAttributing();
        }
        if (!wasConfiguredToDoIndexing()) {
            wasConfiguredToDoIndexing = doesIndexing();
        }
        if (!usesStructOrMap()) {
            new ExpressionVisitor() {

                @Override
                protected void doVisit(Expression exp) {
                    if (!(exp instanceof AttributeExpression)) {
                        return;
                    }
                    String fieldName = ((AttributeExpression)exp).getFieldName();
                    if (fieldName == null) {
                        fieldName = getName();
                    }
                    Attribute attribute = attributes.get(fieldName);
                    if (attribute == null) {
                        addAttribute(new Attribute(fieldName, getDataType()));
                    }
                }
            }.visit(indexingScript);
        }
        for (SDField structField : getStructFields()) {
            structField.setIndexingScript(exp);
        }
    }

    @Override
    public ScriptExpression getIndexingScript() { return indexingScript; }

    @SuppressWarnings("deprecation")
    @Override
    public void setDataType(DataType type) {
        if (type.equals(DataType.URI)) { // Different defaults, naturally
            normalizing.inferLowercase();
            stemming = Stemming.NONE;
        }
        this.dataType = type;
        if ( ! idOverride) {
            this.fieldId = calculateIdV7(null);
        }
    }

    @Override
    public boolean isIndexStructureField() {
        return indexStructureField;
    }

    public void setIndexStructureField(boolean indexStructureField) {
        this.indexStructureField = indexStructureField;
    }

    @Override
    public boolean hasIndex() {
        return (getIndexingScript() != null) && doesIndexing();
    }

    /** Sets the literal boost of this field */
    public void setLiteralBoost(int literalBoost) { this.literalBoost=literalBoost; }

    /**
     * Returns the literal boost of this field. This boost is added to a literal score
     * when a query term matched as query term exactly (unnormalized and unstemmed).
     * Default is non-positive.
     */
    @Override
    public int getLiteralBoost() { return literalBoost; }

    /** Sets the weight of this field */
    public void setWeight(int weight) { this.weight=weight; }

    /** Returns the weight of this field, or 0 if nothing is set */
    @Override
    public int getWeight() { return weight; }

    /**
     * Returns what kind of matching type should be applied.
     */
    @Override
    public Matching getMatching() { return matching; }

    /**
     * Sets what kind of matching type should be applied.
     * (Token matching is default, PREFIX, SUBSTRING, SUFFIX are alternatives)
     */
    public void setMatching(Matching matching) { this.matching=matching; }

    /**
     * Returns Dictionary settings.
     */
    public Dictionary getDictionary() { return dictionary; }
    public Dictionary getOrSetDictionary() {
        if (dictionary == null) {
            dictionary = new Dictionary();
        }
        return dictionary;
    }

    /**
     * Set the matching type for this field and all subfields.
     */
    // TODO: When this is not the same as getMatching().setthis we have a potential for inconsistency. Find the right
    //       Matching object for struct fields at lookup time instead.
    public void setMatchingType(MatchType type) {
        this.getMatching().setType(type);
        for (SDField structField : getStructFields()) {
            structField.setMatchingType(type);
        }
    }

    /**
     * Set the matching type for this field and all subfields.
     */
    // TODO: When this is not the same as getMatching().setthis we have a potential for inconsistency. Find the right
    //       Matching object for struct fields at lookup time instead.
    public void setMatchingCase(Case casing) {
        this.getMatching().setCase(casing);
        for (SDField structField : getStructFields()) {
            structField.setMatchingCase(casing);
        }
    }
    /**
     * Set matching algorithm for this field and all subfields.
     */
    // TODO: When this is not the same as getMatching().setthis we have a potential for inconsistency. Find the right
    //       Matching object for struct fields at lookup time instead.
    public void setMatchingAlgorithm(MatchAlgorithm algorithm) {
        this.getMatching().setAlgorithm(algorithm);
        for (SDField structField : getStructFields()) {
            structField.getMatching().setAlgorithm(algorithm);
        }
    }

    /** Adds an explicit index defined in this field */
    public void addIndex(Index index) {
        indices.put(index.getName(),index);
    }

    /**
     * Returns an index, or null if no index with this name has had
     * some <b>explicit settings</b> applied in this field (even if this returns null,
     * the index may be implicitly defined by an indexing statement)
     */
    @Override
    public Index getIndex(String name) {
        return indices.get(name);
    }

    /**
     * Returns an index if this field has one (implicitly or
     * explicitly) targeting the given name.
     */
    @Override
    public boolean existsIndex(String name) {
        if (indices.get(name) != null) return true;
        return name.equals(getName()) && doesIndexing();
    }

    /**
     * Defined indices on this field
     * @return defined indices on this
     */
    @Override
    public Map<String, Index> getIndices() {
        return indices;
    }

    /**
     * Sets the default rank type of this fields indices, and sets this rank type
     * to all indices explicitly defined here which has no index set.
     * (This complex behavior is dues to the fact than we would prefer to have rank types
     * per field, not per index)
     */
    public void setRankType(RankType rankType) {
        this.rankType=rankType;
        for (Index index : getIndices().values()) {
            if (index.getRankType()==null)
                index.setRankType(rankType);
        }

    }

    /** Returns the rank settings set in a "rank" block for this field. This is never null. */
    @Override
    public Ranking getRanking() { return ranking; }

    /** Returns the default rank type of indices of this field, or null if nothing is set */
    @Override
    public RankType getRankType() { return this.rankType; }

    /**
     * Returns the search-time attribute settings of this field or null if none is set.
     *
     * <p>TODO: Make unmodifiable.</p>
     */
    @Override
    public Map<String, Attribute> getAttributes() { return attributes; }

    public Attribute getAttribute() {
        return attributes.get(getName());
    }

    public void addAttribute(Attribute attribute) {
        String name = attribute.getName();
        if (name == null || "".equals(name)) {
            name = getName();
            attribute.setName(name);
        }
        attributes.put(attribute.getName(),attribute);
    }

    /**
     * Returns the stemming setting of this field.
     * Default is determined by the owning search definition.
     *
     * @return the stemming setting of this, or null, to use the default
     */
    @Override
    public Stemming getStemming() { return stemming; }

    /**
     * Whether this field should be stemmed in this search definition
     */
    @Override
    public Stemming getStemming(Schema schema) {
        if (stemming != null)
            return stemming;
        else
            return schema.getStemming();
    }

    @Override
    public Field asField() {
        return this;
    }

    /**
     * Sets how this field should be stemmed, or set to null to use the default.
     */
    public void setStemming(Stemming stemming) {
        this.stemming = stemming;
    }

    /** Returns an unmodifiable map of the summary fields defined in this */
    @Override
    public Map<String, SummaryField> getSummaryFields() {
        return Collections.unmodifiableMap(summaryFields);
    }

    public void removeSummaryFields() {
        summaryFields.clear();
    }

    /** Adds a summary field */
    public void addSummaryField(SummaryField summaryField) {
        summaryFields.put(summaryField.getName(),summaryField);
    }

    /**
     * Returns a summary field defined (implicitly or explicitly) by this field.
     * Returns null if there is no such summary field defined.
     */
    @Override
    public SummaryField getSummaryField(String name) {
        return summaryFields.get(name);
    }

    /**
     * Returns a summary field defined (implicitly or explicitly) by this field.
     *
     * @param create true to create the summary field and add it to this field before returning if it is missing
     * @return the summary field, or null if not present and create is false
     */
    public SummaryField getSummaryField(String name, boolean create) {
        SummaryField summaryField=summaryFields.get(name);
        if (summaryField==null && create) {
            summaryField=new SummaryField(name, getDataType());
            addSummaryField(summaryField);
        }
        return summaryFields.get(name);
    }

    /** Returns list of static struct fields */
    @Override
    public Collection<SDField> getStructFields() {
        actuallyMakeStructFields();
        return structFields.values();
    }

    /**
     * Returns a struct field defined in this field,
     * potentially traversing into nested structs.
     * Returns null if there is no such struct field defined.
     */
    @Override
    public SDField getStructField(String name) {
        actuallyMakeStructFields();
        if (name.contains(".")) {
            String superFieldName = name.substring(0,name.indexOf("."));
            String subFieldName = name.substring(name.indexOf(".")+1);
            SDField superField = structFields.get(superFieldName);
            if (superField != null) {
                return superField.getStructField(subFieldName);
            }
            return null;
        }
        return structFields.get(name);
    }

    /**
     * Returns how the content of this field should be accent normalized etc
     */
    @Override
    public NormalizeLevel getNormalizing() { return normalizing; }

    /**
     * Change how the content of this field should be accent normalized etc
     */
    public void setNormalizing(NormalizeLevel level) { normalizing = level; }

    public void addQueryCommand(String name) {
       queryCommands.add(name);
    }

    public boolean hasQueryCommand(String name) {
        return queryCommands.contains(name);
    }

    /** Returns a list of query commands */
    @Override
    public List<String> getQueryCommands() { return queryCommands; }

    @Override
    public boolean equals(Object other) {
        if ( ! (other instanceof SDField)) return false;
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public String toString() {
        return "field '" + getName() + "'";
    }

    /** The aliases declared for this field */
    @Override
    public Map<String, String> getAliasToName() {
        return aliasToName;
    }

    @Override
    public boolean hasFullIndexingDocprocRights() {
        Attribute self = getAttributes().get(getName());
        return (!isExtraField() || ((self != null) && self.isMutable()));
    }

}
