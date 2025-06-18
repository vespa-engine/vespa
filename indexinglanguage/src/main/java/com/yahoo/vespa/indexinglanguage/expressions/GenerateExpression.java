package com.yahoo.vespa.indexinglanguage.expressions;

import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.FieldGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Indexing language expression for generating field values with LLMs and custom components.
 *
 * @author glebashnik
 */
public class GenerateExpression extends Expression {

    private final Linguistics linguistics;
    private final Components.Selected<FieldGenerator> generator;

    /** The destination the generated value will be written to in the form [schema name].[field name] */
    private String destination;

    /** The target type we are generating into. */
    private DataType targetType;
    
    public GenerateExpression(Linguistics linguistics,
                              Components<FieldGenerator> generators,
                              String generatorId,
                              List<String> generatorArguments) {
        this.linguistics = linguistics;
        this.generator = new Components.Selected<>("generator", generators, generatorId, true,
                                                   generatorArguments);
    }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        if (!inputType.isAssignableTo(DataType.STRING)) {
            throw new VerificationException(
                    this,
                    "Generate expression for field %s requires string input type, but got %s."
                            .formatted(destination, inputType.getName())
            );
        }

        super.setInputType(inputType, DataType.STRING, context);
        return targetType;
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(outputType, context);
        return DataType.STRING;
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        targetType = field.getDataType();
        destination = documentType.getName() + "." + field.getName();
    }

    @Override
    protected void doResolve(TypeContext context) {
        targetType = getOutputType(context);
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
            var promptString = ((StringFieldValue) inputValue).getString();
            generatedValue = generate(StringPrompt.from(promptString), context);
        } else {
            throw new IllegalArgumentException(
                    ("Generate expression for field %s requires string input type, but got %s.")
                            .formatted(destination, inputType.getName()));
        }
        
        context.setCurrentValue(generatedValue);
    }
    
    private FieldValue generate(Prompt prompt, ExecutionContext context) {
        var generatorContext =  new FieldGenerator.Context(destination, targetType, context.getCache())
                .setLanguage(context.resolveLanguage(linguistics))
                .setComponentId(generator.id());

        return generator.component().generate(prompt, generatorContext);
    }

    @Override
    public String toString() {
        return "generate" + generator.argumentsString();
    }

    @Override
    public int hashCode() { return Objects.hash(GenerateExpression.class, generator); }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof GenerateExpression other)) return false;
        if ( ! other.generator.equals(this.generator)) return false;
        return true;
    }
}
