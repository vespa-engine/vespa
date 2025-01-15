package com.yahoo.vespa.indexinglanguage.expressions;

import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.TextGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Indexing language expression to generate text with TextGenerator component.
 *
 * @author glebashnik
 */
public class GenerateExpression extends Expression {
    private final Linguistics linguistics;
    private final TextGenerator textGenerator;
    private final String generatorId;
    private final List<String> generatorArguments;

    /** The destination the generated value will be written to in the form [schema name].[field name] */
    private String destination;

    /** The target type we are generating into. */
    private DataType targetType;
    
    public GenerateExpression(
            Linguistics linguistics, 
            Map<String, TextGenerator> generators, 
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
            this.textGenerator = generators.entrySet().stream().findFirst().get().getValue();
        }
        else if (generators.size() > 1 && ! generatorIdProvided) {
            this.textGenerator = new TextGenerator.FailingTextGenerator(
                    "Multiple generators are provided but no generator id is given. " +
                    "Valid generators are " + validGenerators(generators));
        }
        else if ( ! generators.containsKey(generatorId)) {
            this.textGenerator = new TextGenerator.FailingTextGenerator("Can't find generator '" + generatorId + "'. " +
                    "Valid generators are " + validGenerators(generators));
        } else  {
            this.textGenerator = generators.get(generatorId);
        }
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        if (! (inputType.isAssignableTo(DataType.STRING))
                && !(inputType instanceof ArrayDataType array && array.getNestedType() == DataType.STRING))
            throw new VerificationException(this, "Generate expression requires either a string or array<string> input type, but got "
                    + inputType.getName());
        
        super.setInputType(inputType, context);
        return inputType; // return output type the same as input type: string or array<string>
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        if (!(DataType.STRING.isAssignableTo(outputType))
                && !(outputType instanceof ArrayDataType array && array.getNestedType() == DataType.STRING))
            throw new VerificationException(this, "Generate expression requires either a string or array<string> output type, but got "
                    + outputType.getName());
        
        super.setOutputType(null, outputType, null, context);
        return outputType; // return input type the same as output type: string or array<string>
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
        if (context.getCurrentValue() == null) {
            return;
        }

        FieldValue inputValue = context.getCurrentValue();
        DataType inputType = inputValue.getDataType();
        
        if (inputType == DataType.STRING) {
            context.setCurrentValue(generateSingleValue(context));
        }
        else if (inputType instanceof ArrayDataType arrayType && arrayType.getNestedType() == DataType.STRING) {
            context.setCurrentValue(generateArrayValue(context));
        }
        else {
            throw new IllegalArgumentException("Generate expression requires either a string or array<string> input type, but got " +
                    context.getCurrentValue().getDataType());
        }

    }

    private StringFieldValue generateSingleValue(ExecutionContext context) {
        StringFieldValue input = (StringFieldValue)context.getCurrentValue();
        String output = generate(input.getString(), context);
        return new StringFieldValue(output);
    }
    
    private Array<StringFieldValue> generateArrayValue(ExecutionContext context) {
        @SuppressWarnings("unchecked")
        var inputArrayValue = (Array<StringFieldValue>)context.getCurrentValue();
        var outputArrayValue = new Array<StringFieldValue>(new ArrayDataType(DataType.STRING));

        for (StringFieldValue inputStringValue : inputArrayValue) {
            String output = generate(inputStringValue.getString(), context);
            outputArrayValue.add(new StringFieldValue(output));
        }
        
        return outputArrayValue;
    }


    private String generate(String input, ExecutionContext context) {
        var textGeneratorContext =  new TextGenerator.Context(destination, context.getCache())
                .setLanguage(context.resolveLanguage(linguistics))
                .setGeneratorId(generatorId);
        
        return textGenerator.generate(StringPrompt.from(input), textGeneratorContext);
    }

    @Override
    public DataType createdOutputType() {
        return targetType;
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

    private static String validGenerators(Map<String, TextGenerator> generators) {
        List<String> generatorIds = new ArrayList<>();
        generators.forEach((key, value) -> generatorIds.add(key));
        generatorIds.sort(null);
        return String.join(", ", generatorIds);
    }
}
