// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import com.yahoo.document.*;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.searchdefinition.Index;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.fieldoperation.FieldOperation;
import com.yahoo.searchdefinition.fieldoperation.FieldOperationContainer;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.indexinglanguage.ExpressionSearcher;
import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.expressions.*;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.io.Serializable;
import java.util.*;

/**
 * The field class represents a document field. It is used in
 * the Document class to get and set fields. Each SDField has
 * a name, a numeric ID, a data type, and a boolean that says whether it's
 * a header field. The numeric ID is used when the fields are stored
 * in serialized form.
 *
 * @author bratseth
 */
public class SDField extends Field implements TypedKey, FieldOperationContainer, ImmutableSDField, Serializable {

    /** Use this field for modifying index-structure, even if it doesn't
        have any indexing code */
    private boolean indexStructureField = false;

    /** The indexing statements to be applied to this value during indexing */
    private ScriptExpression indexingScript = new ScriptExpression();

    /** The default rank type for indices of this field */
    private RankType rankType = RankType.DEFAULT;

    /** Rank settings in a "rank" block for the field. */
    private Ranking ranking = new Ranking();

    /**
     * The literal boost of this field. This boost is added to a rank score
     * when a query term matched as query term exactly (unnormalized and unstemmed).
     * Non-positive boosts causes no boosting, 0 allows boosts
     * to be specified in other rank profiles, while negative values
     * turns the capability off.
     */
    private int literalBoost=-1;

    /** 
     * The weight of this field. This is a percentage,
     * so 100 is default to provide the identity transform. 
     */
    private int weight=100;

    /**
     * Indicates what kind of matching should be done on this field
     */
    private Matching matching=new Matching();

    /** Attribute settings, or null if there are none */
    private Map<String, Attribute> attributes = new TreeMap<>();

    /**
     * The stemming setting of this field, or null to use the default.
     * Default is determined by the owning search definition.
     */
    private Stemming stemming=null;

    /** How content of this field should be accent normalized etc. */
    private NormalizeLevel normalizing = new NormalizeLevel();

    /** Extra query commands of this field */
    private List<String> queryCommands=new java.util.ArrayList<>(0);

    /** Summary fields defined in this field */
    private Map<String,SummaryField> summaryFields = new java.util.LinkedHashMap<>(0);

    /** The explicitly index settings on this field */
    private Map<String, Index> indices=new java.util.LinkedHashMap<>();

    /** True if body or header is set explicitly for this field */
    private boolean headerOrBodyDefined = false;

    private boolean idOverride = false;

    /** Struct fields defined in this field */
    private Map<String,SDField> structFields = new java.util.LinkedHashMap<>(0);

    /** The document that this field was declared in, or null*/
    protected SDDocumentType ownerDocType = null;

    /** The aliases declared for this field. May pertain to indexes or attributes */
    private Map<String, String> aliasToName = new HashMap<>();

    /** Pending operations that must be applied after parsing, due to use of not-yet-defined structs. */
    private List<FieldOperation> pendingOperations = new LinkedList<>();

    private boolean isExtraField = false;

    /**
       Creates a new field. This method is only used to create reserved fields
       @param name The name of the field
       @param dataType The datatype of the field
       @param isHeader Whether this is a "header" field or a "content" field
       (true = "header").
    */
    protected SDField(SDDocumentType repo, String name, int id, DataType dataType, boolean isHeader, boolean populate) {
        super(name, id, dataType, isHeader);
        populate(populate, repo, name, dataType, isHeader);
    }

    public SDField(SDDocumentType repo, String name, int id, DataType dataType, boolean isHeader) {
        this(repo, name, id, dataType, isHeader, true);
    }

    /**
       Creates a new field.

       @param name The name of the field
       @param dataType The datatype of the field
       @param isHeader Whether this is a "header" field or a "content" field
       (true = "header").
    */
    public SDField(SDDocumentType repo, String name, DataType dataType, boolean isHeader, boolean populate) {
        super(name,dataType,isHeader);
        populate(populate, repo, name, dataType, isHeader);
    }

    private void populate(boolean populate, SDDocumentType repo, String name, DataType dataType, boolean isHeader) {
        populate(populate,repo, name, dataType, isHeader, null, 0);
    }

    private void populate(boolean populate, SDDocumentType repo, String name, DataType dataType, boolean isHeader, Matching fieldMatching,  int recursion) {
        if (populate || (dataType instanceof MapDataType)) {
            populateWithStructFields(repo, name, dataType, isHeader, recursion);
            populateWithStructMatching(repo, name, dataType, fieldMatching);
        }
    }

    public SDField(String name, DataType dataType, boolean isHeader) {
        this(null, name, dataType, isHeader, true);
    }

    /**
       Creates a new field.

       @param name The name of the field
       @param dataType The datatype of the field
       @param isHeader Whether this is a "header" field or a "content" field
       (true = "header").
       @param owner the owning document (used to check for id collisions)
    */
    protected SDField(SDDocumentType repo, String name, DataType dataType, boolean isHeader, SDDocumentType owner, boolean populate) {
        super(name, dataType, isHeader, owner == null ? null : owner.getDocumentType());
        this.ownerDocType=owner;
        populate(populate, repo, name, dataType, isHeader);
    }

    /**
       Creates a new field.

       @param name The name of the field
       @param dataType The datatype of the field
       @param isHeader Whether this is a "header" field or a "content" field
       (true = "header").
       @param owner The owning document (used to check for id collisions)
       @param fieldMatching The matching object to set for the field
    */
    protected SDField(SDDocumentType repo, String name, DataType dataType, boolean isHeader, SDDocumentType owner, Matching fieldMatching, boolean populate, int recursion) {
        super(name, dataType, isHeader, owner == null ? null : owner.getDocumentType());
        this.ownerDocType=owner;
        if (fieldMatching != null) {
            this.setMatching(fieldMatching);
        }
        populate(populate, repo, name, dataType, isHeader, fieldMatching, recursion);
    }

    /**
       Constructor for <b>header</b> fields

       @param name The name of the field
       @param dataType The datatype of the field
    */
    public SDField(SDDocumentType repo,  String name, DataType dataType) {
        this(repo, name,dataType,true, true);
    }
    public SDField(String name, DataType dataType) {
        this(null, name,dataType);
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
    public ImmutableSDField getBackingField() { return this; }

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

    public <T extends Expression> T findExpression(Class<T> searchFor) {
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
                for (SummaryField sumF : structField.getSummaryFields()) {
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

    public void populateWithStructFields(SDDocumentType sdoc, String name, DataType dataType, boolean isHeader, int recursion) {
        DataType dt = getFirstStructOrMapRecursive();
        if (dt == null) {
            return;
        }
        if (dataType instanceof MapDataType) {
            MapDataType mdt = (MapDataType) dataType;

            SDField keyField = new SDField(sdoc, name.concat(".key"), mdt.getKeyType(),
                                           isHeader, getOwnerDocType(), new Matching(), true, recursion + 1);
            structFields.put("key", keyField);

            SDField valueField = new SDField(sdoc, name.concat(".value"), mdt.getValueType(),
                                             isHeader, getOwnerDocType(), new Matching(), true, recursion + 1);
            structFields.put("value", valueField);
        } else {
            if (recursion >= 10) {
                return;
            }
            if (dataType instanceof CollectionDataType) {
                dataType = ((CollectionDataType)dataType).getNestedType();
            }
            SDDocumentType subType = sdoc != null ? sdoc.getType(dataType.getName()) : null;
            if (subType == null) {
                throw new IllegalArgumentException("Could not find struct '" + dataType.getName() + "'.");
            }
            for (Field field : subType.fieldSet()) {
                SDField subField = new SDField(sdoc, name.concat(".").concat(field.getName()), field.getDataType(),
                                               isHeader, subType, new Matching(), true, recursion + 1);
                structFields.put(field.getName(), subField);
            }
        }
    }

    public void populateWithStructMatching(SDDocumentType sdoc, String name, DataType dataType,
                                           Matching superFieldMatching) {
        DataType dt = getFirstStructOrMapRecursive();
        if (dt != null) {
            if (dataType instanceof MapDataType) {
                MapDataType mdt = (MapDataType) dataType;

                Matching keyFieldMatching = new Matching();
                if (superFieldMatching != null) {
                    keyFieldMatching.merge(superFieldMatching);
                }
                SDField keyField = structFields.get(name.concat(".key"));
                if (keyField != null) {
                    keyField.populateWithStructMatching(sdoc, name.concat(".key"), mdt.getKeyType(), keyFieldMatching);
                    keyField.setMatching(keyFieldMatching);
                }

                Matching valueFieldMatching = new Matching();
                if (superFieldMatching != null) {
                    valueFieldMatching.merge(superFieldMatching);
                }
                SDField valueField = structFields.get(name.concat(".value"));
                if (valueField != null) {
                    valueField.populateWithStructMatching(sdoc, name.concat(".value"), mdt.getValueType(),
                                                          valueFieldMatching);
                    valueField.setMatching(valueFieldMatching);
                }

            } else {

                if (dataType instanceof CollectionDataType) {
                    dataType = ((CollectionDataType)dataType).getNestedType();
                }
                SDDocumentType subType = sdoc != null ? sdoc.getType(dataType.getName()) : null;
                if (subType != null) {
                    for (Field f : subType.fieldSet()) {
                        if (f instanceof SDField) {
                            SDField field = (SDField)f;
                            Matching subFieldMatching = new Matching();
                            if (superFieldMatching != null) {
                                subFieldMatching.merge(superFieldMatching);
                            }
                            subFieldMatching.merge(field.getMatching());
                            SDField subField = structFields.get(field.getName());
                            if (subField != null) {
                                subField.populateWithStructMatching(sdoc, name.concat(".").concat(field.getName()), field.getDataType(),
                                                                    subFieldMatching);
                                subField.setMatching(subFieldMatching);
                            }
                        } else {
                             throw new IllegalArgumentException("Field in struct is not SDField " + f.getName());
                        }
                    }
                } else {
                    throw new IllegalArgumentException("Could not find struct " + dataType.getName());
                }
            }
        }
    }

    public void addOperation(FieldOperation op) {
        pendingOperations.add(op);
    }

    @Override
    public void applyOperations(SDField field) {
        if (pendingOperations.isEmpty()) return;

        Collections.sort(pendingOperations);
        ListIterator<FieldOperation> ops = pendingOperations.listIterator();
        while (ops.hasNext()) {
            FieldOperation op = ops.next();
            ops.remove();
            op.apply(field);
        }
    }

    public void applyOperations() {
        applyOperations(this);
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

    public DataType getFirstStructOrMapRecursive() {
        DataType dataType = getDataType();
        while (dataType instanceof CollectionDataType) { // Currently no nesting of collections
            dataType = ((CollectionDataType)dataType).getNestedType();
        }
        return (dataType instanceof StructDataType || dataType instanceof MapDataType) ? dataType : null;
    }

    public boolean usesStruct() {
        DataType dt = getFirstStructRecursive();
        return (dt != null);
    }

    @Override
    public boolean usesStructOrMap() {
        DataType dt = getFirstStructOrMapRecursive();
        return (dt != null);
    }

    /** Parse an indexing expression which will use the simple linguistics implementatino suitable for testing */
    public void parseIndexingScript(String script) {
        parseIndexingScript(script, new SimpleLinguistics());
    }

    public void parseIndexingScript(String script, Linguistics linguistics) {
        try {
            ScriptParserContext config = new ScriptParserContext(linguistics);
            config.setInputStream(new IndexingInput(script));
            setIndexingScript(ScriptExpression.newInstance(config));
        } catch (ParseException e) {
            throw new RuntimeException("Failed to parser script '" + script + "'.", e);
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
    public ScriptExpression getIndexingScript() {
        return indexingScript;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setDataType(DataType type) {
        if (type.equals(DataType.URI)) { // Different defaults, naturally
            normalizing.inferLowercase();
            stemming=Stemming.NONE;
        }
        this.dataType = type;
        if (!idOverride) {
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

    /**
     * Returns an iterator of the index names this should index to
     * (whether set explicitly or not)
     */
    public Iterator<String> getFieldNameAsIterator() { // TODO: Replace usage by getName
        return Collections.singletonList(getName()).iterator();
    }

    /** Returns 1 if this is indexed, 0 if it is not indexed */ // TODO: Replace by a boolean method, or something, see hasIndex
    public int getIndexToCount() {
        if (getIndexingScript() == null) return 0;
        if (!doesIndexing()) return 0;

        return 1;
    }

    /** Sets the literal boost of this field */
    public void setLiteralBoost(int literalBoost) { this.literalBoost=literalBoost; }

    /**
     * Returns the literal boost of this field. This boost is added to a literal score
     * when a query term matched as query term exactly (unnormalized and unstemmed).
     * Default is non-positive.
     */
    public int getLiteralBoost() { return literalBoost; }

    /** Sets the weight of this field */
    public void setWeight(int weight) { this.weight=weight; }

    /** Returns the weight of this field, or 0 if nothing is set */
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
     * Set the matching type for this field and all subfields.
     */
    // TODO: When this is not the same as getMatching().setthis we have a potential for inconsistency. Find the right
    //       Matching object for struct fields as lookup time instead.
    public void setMatchingType(Matching.Type type) {
        this.getMatching().setType(type);
        for (SDField structField : getStructFields()) {
            structField.setMatchingType(type);
        }
    }

    /**
     * Set matching algorithm for this field and all subfields.
     */
    // TODO: When this is not the same as getMatching().setthis we have a potential for inconsistency. Find the right
    //       Matching object for struct fields as lookup time instead.
    public void setMatchingAlgorithm(Matching.Algorithm algorithm) {
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
    public boolean existsIndex(String name) {
        if (indices.get(name) != null) return true;
        if (name.equals(getName())) {
            if (doesIndexing()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Defined indices on this field
     * @return defined indices on this
     */
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
    public RankType getRankType() { return this.rankType; }

    /**
     * Returns the search-time attribute settings of this field or null if none is set.
     *
     * <p>TODO: Make unmodifiable.</p>
     */
    @Override
    public Map<String, Attribute> getAttributes() { return attributes; }

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
    public Stemming getStemming(Search search) {
        if (stemming!=null)
            return stemming;
        else
            return search.getStemming();
    }

    @Override
    public Field asField() {
        return this;
    }

    /**
     * Sets how this field should be stemmed, or set to null to use the default.
     */
    public void setStemming(Stemming stemming) {
        this.stemming=stemming;
    }

    /**
     * List of static summary fields
     * @return list of static summary fields
     */
    public Collection<SummaryField> getSummaryFields() { return summaryFields.values(); }

    /**
     * Add summary field
     */
    public void addSummaryField(SummaryField summaryField) {
        summaryFields.put(summaryField.getName(),summaryField);
    }

    /**
     * Returns a summary field defined (implicitly or explicitly) by this field.
     * Returns null if there is no such summary field defined.
     */
    public SummaryField getSummaryField(String name) {
        return summaryFields.get(name);
    }

    /**
     * Returns a summary field defined (implicitly or explicitly) by this field.
     *
     * @param create true to create the summary field and add it to this field before returning if it is missing
     * @return the summary field, or null if not present and create is false
     */
    public SummaryField getSummaryField(String name,boolean create) {
        SummaryField summaryField=summaryFields.get(name);
        if (summaryField==null && create) {
            summaryField=new SummaryField(name, getDataType());
            addSummaryField(summaryField);
        }
        return summaryFields.get(name);
    }

    /** Returns list of static struct fields */
    @Override
    public Collection<SDField> getStructFields() { return structFields.values(); }

    /**
     * Returns a struct field defined in this field,
     * potentially traversing into nested structs.
     * Returns null if there is no such struct field defined.
     */
    @Override
    public SDField getStructField(String name) {
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

    /**
     * A list of query commands
     * @return a list of strings with query commands.
     */
    @Override
    public List<String> getQueryCommands() {
        return queryCommands;
    }


    public boolean isHeaderOrBodyDefined() {
        return headerOrBodyDefined;
    }

    public void setHeaderOrBodyDefined(boolean headerOrBodySetExplicitly) {
        this.headerOrBodyDefined = headerOrBodySetExplicitly;
    }

    /**
     * The document that this field was declared in, or null
     *
     */
    public SDDocumentType getOwnerDocType() {
        return ownerDocType;
    }

    /**
     * Two fields are equal if they have the same name
     * No, they are not.
     */
    public boolean equals(Object other) {
        if ( ! (other instanceof SDField)) return false;
        return super.equals(other);
    }

    public int hashCode() {
        return getName().hashCode();
    }

    public String toString() {
        return "field '" + getName() + "'";
    }

    /** The aliases declared for this field */
    @Override
    public Map<String, String> getAliasToName() {
        return aliasToName;
    }

}
