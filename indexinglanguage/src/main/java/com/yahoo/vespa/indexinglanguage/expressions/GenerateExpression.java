package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.FieldGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Indexing language expression for generating field values with LLMs and custom components.
 *
 * @author glebashnik
 */
public class GenerateExpression extends Expression {
    private final Linguistics linguistics;
    private final FieldGenerator generator;
    private final String generatorId;
    private final List<String> generatorArguments;

    /** The destination the generated value will be written to in the form [schema name].[field name] */
    private String destination;

    /** The target type we are generating into. */
    private DataType targetType;
    
    public GenerateExpression(
            Linguistics linguistics, 
            Map<String, FieldGenerator> generators, 
            String generatorId, 
            List<String> generatorArguments
    ) {
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
            this.generator = new FieldGenerator.FailingFieldGenerator(
                    "Multiple generators are provided but no generator id is given. " +
                    "Valid generators are " + validGenerators(generators));
        }
        else if ( ! generators.containsKey(generatorId)) {
            this.generator = new FieldGenerator.FailingFieldGenerator("Can't find generator '" + generatorId + "'. " +
                    "Valid generators are " + validGenerators(generators));
        } else  {
            this.generator = generators.get(generatorId);
        }
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, DataType.STRING, context);
        return targetType;
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(targetType, outputType, null, context);
        return DataType.STRING;
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        targetType = field.getDataType();
        destination = documentType.getName() + "." + field.getName();
    }

    @Override
    protected void doVerify(VerificationContext context) {
        targetType = getOutputType(context);
        context.setCurrentType(targetType);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        if (context.getCurrentValue() == null) {
            return;
        }

        FieldValue inputValue = context.getCurrentValue();
        DataType inputType = inputValue.getDataType();
        FieldValue generatedValue;
        
        if (inputType == DataType.STRING) {
            generatedValue = generate(((StringFieldValue) inputValue).getString(), context);
        } else {
            throw new IllegalArgumentException(
                    ("Generate expression for field %s requires a string as input type, but got %s.")
                            .formatted(destination, inputType.getName()));
        }
        
        context.setCurrentValue(generatedValue);
    }
    
    private FieldValue generate(String input, ExecutionContext context) {
        var generatorContext =  new FieldGenerator.Context(destination, targetType, context.getCache())
                .setLanguage(context.resolveLanguage(linguistics))
                .setGeneratorId(generatorId);

        return generator.generate(input, generatorContext);
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

    private static String validGenerators(Map<String, FieldGenerator> generators) {
        List<String> generatorIds = new ArrayList<>();
        generators.forEach((key, value) -> generatorIds.add(key));
        generatorIds.sort(null);
        return String.join(", ", generatorIds);
    }
}
