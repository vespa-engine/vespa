package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates a value using the configured Generator component
 *
 * @author glebashnik
 */
public class GenerateExpression extends Expression {
    private final Linguistics linguistics;
    private final Generator generator;
    private final String generatorId;
    private final List<String> generatorArguments;

    /** The destination the generated value will be written to in the form [schema name].[field name] */
    private String destination;

    /** The target type we are generating into. */
    private DataType targetType;
    
    public GenerateExpression(
            Linguistics linguistics, 
            Map<String, Generator> generators, 
            String generatorId, 
            List<String> generatorArguments
    ) {
        super(DataType.STRING);
        this.linguistics = linguistics;
        this.generatorId = generatorId;
        this.generatorArguments = List.copyOf(generatorArguments);

        boolean generatorIdProvided = generatorId != null && !generatorId.isEmpty();

        if (generators.isEmpty()) {
            throw new IllegalStateException("No generators provided");  // should never happen
        }
        else if (generators.size() == 1 && ! generatorIdProvided) {
            this.generator = generators.entrySet().stream().findFirst().get().getValue();
        }
        else if (generators.size() > 1 && ! generatorIdProvided) {
            this.generator = new Generator.FailingGenerator(
                    "Multiple generators are provided but no generator id is given. " +
                    "Valid generators are " + validGenerators(generators));
        }
        else if ( ! generators.containsKey(generatorId)) {
            this.generator = new Generator.FailingGenerator("Can't find generator '" + generatorId + "'. " +
                    "Valid generators are " + validGenerators(generators));
        } else  {
            this.generator = generators.get(generatorId);
        }
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        return super.setInputType(inputType, DataType.STRING, context);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        return super.setOutputType(DataType.STRING, outputType, context);
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        targetType = field.getDataType();
        destination = documentType.getName() + "." + field.getName();
    }

    @Override
    protected void doVerify(VerificationContext context) {
        targetType = getOutputType(context);
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        if (context.getCurrentValue() == null) return;
        
        String output;
        if (context.getCurrentValue().getDataType() == DataType.STRING) {
            output = generateSingleValue(context);
        }
        else {
            throw new IllegalArgumentException("Generate can only be done on string fields, not " +
                    context.getCurrentValue().getDataType());
        }
        
        context.setCurrentValue(new StringFieldValue(output));
    }

    private String generateSingleValue(ExecutionContext context) {
        StringFieldValue input = (StringFieldValue)context.getCurrentValue();
        return generate(input.getString(), targetType, context);
    }

    private String generate(String input, DataType targetType, ExecutionContext context) {
        return generator.generate(
                input,
                new Generator.Context(destination, context.getCache())
                        .setLanguage(context.resolveLanguage(linguistics))
                        .setGeneratorId(generatorId)
        );
    }

    @Override
    public DataType createdOutputType() {
        return targetType;
    }

    private boolean validTarget(DataType target) {
        return target == DataType.STRING;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("generate");
        if (this.generatorId != null && !this.generatorId.isEmpty())
            sb.append(" ").append(this.generatorId);
        generatorArguments.forEach(arg -> sb.append(" ").append(arg));
        return sb.toString();
    }

    @Override
    public int hashCode() { return GenerateExpression.class.hashCode(); }

    @Override
    public boolean equals(Object o) {
        return o instanceof GenerateExpression;
    }

    private static String validGenerators(Map<String, Generator> generators) {
        List<String> generatorIds = new ArrayList<>();
        generators.forEach((key, value) -> generatorIds.add(key));
        generatorIds.sort(null);
        return String.join(", ", generatorIds);
    }
}
