// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.yahoo.search.yql.yqlplusParser.Annotate_expressionContext;
import com.yahoo.search.yql.yqlplusParser.AnnotationContext;
import com.yahoo.search.yql.yqlplusParser.ArgumentContext;
import com.yahoo.search.yql.yqlplusParser.ArgumentsContext;
import com.yahoo.search.yql.yqlplusParser.Array_literalContext;
import com.yahoo.search.yql.yqlplusParser.Call_sourceContext;
import com.yahoo.search.yql.yqlplusParser.Constant_expressionContext;
import com.yahoo.search.yql.yqlplusParser.Dereferenced_expressionContext;
import com.yahoo.search.yql.yqlplusParser.Equality_expressionContext;
import com.yahoo.search.yql.yqlplusParser.Field_defContext;
import com.yahoo.search.yql.yqlplusParser.IdentContext;
import com.yahoo.search.yql.yqlplusParser.In_not_in_targetContext;
import com.yahoo.search.yql.yqlplusParser.LimitContext;
import com.yahoo.search.yql.yqlplusParser.Literal_elementContext;
import com.yahoo.search.yql.yqlplusParser.Literal_listContext;
import com.yahoo.search.yql.yqlplusParser.Logical_AND_expressionContext;
import com.yahoo.search.yql.yqlplusParser.Logical_OR_expressionContext;
import com.yahoo.search.yql.yqlplusParser.Map_expressionContext;
import com.yahoo.search.yql.yqlplusParser.Multiplicative_expressionContext;
import com.yahoo.search.yql.yqlplusParser.Namespaced_nameContext;
import com.yahoo.search.yql.yqlplusParser.OffsetContext;
import com.yahoo.search.yql.yqlplusParser.OrderbyContext;
import com.yahoo.search.yql.yqlplusParser.Orderby_fieldContext;
import com.yahoo.search.yql.yqlplusParser.Output_specContext;
import com.yahoo.search.yql.yqlplusParser.Pipeline_stepContext;
import com.yahoo.search.yql.yqlplusParser.ProgramContext;
import com.yahoo.search.yql.yqlplusParser.Project_specContext;
import com.yahoo.search.yql.yqlplusParser.Property_name_and_valueContext;
import com.yahoo.search.yql.yqlplusParser.Query_statementContext;
import com.yahoo.search.yql.yqlplusParser.Relational_expressionContext;
import com.yahoo.search.yql.yqlplusParser.Relational_opContext;
import com.yahoo.search.yql.yqlplusParser.Scalar_literalContext;
import com.yahoo.search.yql.yqlplusParser.Select_source_multiContext;
import com.yahoo.search.yql.yqlplusParser.Select_statementContext;
import com.yahoo.search.yql.yqlplusParser.Sequence_sourceContext;
import com.yahoo.search.yql.yqlplusParser.Source_listContext;
import com.yahoo.search.yql.yqlplusParser.Source_specContext;
import com.yahoo.search.yql.yqlplusParser.Source_statementContext;
import com.yahoo.search.yql.yqlplusParser.StatementContext;
import com.yahoo.search.yql.yqlplusParser.TimeoutContext;
import com.yahoo.search.yql.yqlplusParser.Unary_expressionContext;
import com.yahoo.search.yql.yqlplusParser.WhereContext;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Translate the ANTLR grammar into the logical representation.
 */
final class ProgramParser {

    public yqlplusParser prepareParser(String programName, String input) {
        return prepareParser(programName, new CaseInsensitiveCharStream(CharStreams.fromString(input)));
    }

    private static class ErrorListener extends BaseErrorListener {
        private final String programName;
        ErrorListener(String programName) { this.programName = programName; }
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol,
                                int line,
                                int charPositionInLine,
                                String msg,
                                RecognitionException e) {
            throw new ProgramCompileException(new Location(programName, line, charPositionInLine), "%s", msg);
        }
    }

    private yqlplusParser prepareParser(String programName, CharStream input) {
        ErrorListener errorListener = new ErrorListener(programName);
        yqlplusLexer lexer = new yqlplusLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        TokenStream tokens = new CommonTokenStream(lexer);

        yqlplusParser parser = new yqlplusParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
        return parser;
    }

    private ProgramContext parseProgram(yqlplusParser parser) throws  RecognitionException {
        try {
            return parser.program();
        } catch (RecognitionException e) {
            // Retry parsing using full LL mode
            parser.reset();
            parser.getInterpreter().setPredictionMode(PredictionMode.LL);
            return parser.program();
        }
    }

    public OperatorNode<StatementOperator> parse(String programName, String program) throws IOException, RecognitionException {
        yqlplusParser parser = prepareParser(programName, program);
        return convertProgram(parseProgram(parser), parser, programName);
    }

    private Location toLocation(Scope scope, ParseTree node) {
        Token start;
        if (node instanceof ParserRuleContext) {
            start = ((ParserRuleContext)node).start;
        } else if (node instanceof TerminalNode) {
            start = ((TerminalNode)node).getSymbol();
        } else {
        	throw new ProgramCompileException("Location is not available for type " + node.getClass());
        }
        return new Location(scope != null? scope.programName: "<string>", start.getLine(), start.getCharPositionInLine());
    }

    private List<String> readName(Namespaced_nameContext node) {
        return node.children.stream()
                .filter(elt -> !(getParseTreeIndex(elt) == yqlplusParser.DOT))
                .map(ParseTree::getText).toList();
    }

    static class Binding {

        private final List<String> binding;

        Binding(List<String> binding) {
            this.binding = binding;
        }

        public List<String> toPath() {
            return binding;
        }

        public List<String> toPathWith(List<String> rest) {
            return Stream.concat(toPath().stream(), rest.stream()).toList();
        }

    }

    static class Scope {

        final Scope root;
        final Scope parent;
        Set<String> cursors = Set.of();
        Set<String> variables = Set.of();
        Map<String, Binding> bindings = new HashMap<>();
        final yqlplusParser parser;
        final String programName;

        Scope(yqlplusParser parser, String programName) {
            this.parser = parser;
            this.programName = programName;
            this.root = this;
            this.parent = null;
        }

        Scope(Scope root, Scope parent) {
            this.root = root;
            this.parent = parent;
            this.parser = parent.parser;
            this.programName = parent.programName;
        }

        public yqlplusParser getParser() {
            return parser;
        }

        public Set<String> getCursors() {
            return cursors;
        }

        boolean isBound(String name) {
            // bindings live only in the 'root' node
            return root.bindings.containsKey(name);
        }

        public Binding getBinding(String name) {
            return root.bindings.get(name);
        }

        public List<String> resolvePath(List<String> path) {
            if (path.size() < 1 || !isBound(path.get(0))) {
                return path;
            } else {
                return getBinding(path.get(0)).toPathWith(path.subList(1, path.size()));
            }
        }

        boolean isCursor(String name) {
            return cursors.contains(name) || (parent != null && parent.isCursor(name));
        }

        boolean isVariable(String name) {
            return variables.contains(name) || (parent != null && parent.isVariable(name));
        }

        public void defineDataSource(Location loc, String name) {
            if (isCursor(name)) {
                throw new ProgramCompileException(loc, "Alias '%s' is already used.", name);
            }
            if (cursors.isEmpty()) {
                cursors = new HashSet<>();
            }
            cursors.add(name);
        }

        public void defineVariable(Location loc, String name) {
            if (isVariable(name)) {
                throw new ProgramCompileException(loc, "Variable/argument '%s' is already used.", name);
            }
            if (variables.isEmpty()) {
                variables = new HashSet<>();
            }
            variables.add(name);

        }

        Scope child() {
            return new Scope(root, this);
        }

        Scope getRoot() {
            return root;
        }
    }

    private OperatorNode<SequenceOperator> convertSelect(ParseTree node, Scope scopeParent) {

        Preconditions.checkArgument(node instanceof Select_statementContext);

        // SELECT^ select_field_spec select_source where? orderby? limit? offset? timeout?
        // select is the only place to define where/orderby/limit/offset
        Scope scope = scopeParent.child();
        ProjectionBuilder proj = null;
        OperatorNode<SequenceOperator> source = null;
        OperatorNode<ExpressionOperator> filter = null;
        List<OperatorNode<SortOperator>> orderby = null;
        OperatorNode<ExpressionOperator> offset = null;
        OperatorNode<ExpressionOperator> limit = null;
        OperatorNode<ExpressionOperator> timeout = null;

        ParseTree sourceNode = node.getChild(2) != null ?  node.getChild(2).getChild(0):null;

        if (sourceNode != null) {
            switch (getParseTreeIndex(sourceNode)) {
                // ALL_SOURCE and MULTI_SOURCE are how FROM SOURCES
                // *|source_name,... are parsed
                case yqlplusParser.RULE_select_source_all -> {
                    Location location = toLocation(scope, sourceNode.getChild(2));
                    source = OperatorNode.create(location, SequenceOperator.ALL);
                    source.putAnnotation("alias", "row");
                    scope.defineDataSource(location, "row");
                }
                case yqlplusParser.RULE_select_source_multi -> {
                    Source_listContext multiSourceContext = ((Select_source_multiContext) sourceNode).source_list();
                    source = readMultiSource(scope, multiSourceContext);
                    source.putAnnotation("alias", "row");
                    scope.defineDataSource(toLocation(scope, multiSourceContext), "row");
                }
                case yqlplusParser.RULE_select_source_from ->
                        source = convertSource((ParserRuleContext) sourceNode.getChild(1), scope);
            }
        } else {
            source = OperatorNode.create(SequenceOperator.EMPTY);
        }

        for (int i = 1; i < node.getChildCount(); ++i) {
            ParseTree child = node.getChild(i);
            switch (getParseTreeIndex(child)) {
                case yqlplusParser.RULE_select_field_spec:
                    if (getParseTreeIndex(child.getChild(0)) == yqlplusParser.RULE_project_spec) {
                        proj = readProjection(((Project_specContext) child.getChild(0)).field_def(), scope);
                    }
                    break;
                case yqlplusParser.RULE_where:
                    filter = convertExpr(((WhereContext) child).expression(), scope);
                    break;
                case yqlplusParser.RULE_orderby:
                    // OrderbyContext orderby()
                    List<Orderby_fieldContext> orderFieds = ((OrderbyContext) child)
                                                                    .orderby_fields().orderby_field();
                    orderby = new ArrayList<>(orderFieds.size());
                    for (var field: orderFieds) {
                        orderby.add(convertSortKey(field, scope));
                    }
                    break;
                case yqlplusParser.RULE_limit:
                    limit = convertExpr(((LimitContext) child).fixed_or_parameter(), scope);
                    break;
                case yqlplusParser.RULE_offset:
                    offset = convertExpr(((OffsetContext) child).fixed_or_parameter(), scope);
                    break;
                case yqlplusParser.RULE_timeout:
                    timeout = convertExpr(((TimeoutContext) child).fixed_or_parameter(), scope);
                    break;
            }
        }
        // now assemble the logical plan
        OperatorNode<SequenceOperator> result = source;
        // filter
        if (filter != null) {
            result = OperatorNode.create(SequenceOperator.FILTER, result, filter);
        }
        // then sort (or project and sort)
        boolean projectBeforeSort = false;
        if (orderby != null) {
            if (proj != null) {
                for (OperatorNode<SortOperator> sortKey : orderby) {
                    OperatorNode<ExpressionOperator> sortExpression = sortKey.getArgument(0);
                    List<OperatorNode<ExpressionOperator>> sortReadFields = getReadFieldExpressions(sortExpression);
                    for (OperatorNode<ExpressionOperator> sortReadField : sortReadFields) {
                        String sortKeyField = sortReadField.getArgument(1);
                        if (proj.isAlias(sortKeyField)) {
                            // TODO: Add support for "mixed" case
                            projectBeforeSort = true;
                            break;
                        }
                    }
                }
            }
            if (projectBeforeSort) {
                result = OperatorNode.create(SequenceOperator.SORT, proj.make(result), orderby);
            } else {
                result = OperatorNode.create(SequenceOperator.SORT, result, orderby);
            }
        }
        // then offset/limit (must be done after sorting!)
        if (offset != null && limit != null) {
            result = OperatorNode.create(SequenceOperator.SLICE, result, offset, limit);
        } else if (offset != null) {
            result = OperatorNode.create(SequenceOperator.OFFSET, result, offset);
        } else if (limit != null) {
            result = OperatorNode.create(SequenceOperator.LIMIT, result, limit);
        }
        // finally, project (if not already)
        if (proj != null && !projectBeforeSort) {
            result = proj.make(result);
        }
        if (timeout != null) {
            result = OperatorNode.create(SequenceOperator.TIMEOUT, result, timeout);
        }
        return result;
    }

    private OperatorNode<SequenceOperator> readMultiSource(Scope scope, Source_listContext multiSource) {
        List<List<String>> sourceNameList = new ArrayList<>();
        List<Namespaced_nameContext> nameSpaces = multiSource.namespaced_name();
        for(Namespaced_nameContext node : nameSpaces) {
            List<String> name = readName(node);
            sourceNameList.add(name);
        }
        return OperatorNode.create(toLocation(scope, multiSource), SequenceOperator.MULTISOURCE, sourceNameList);
    }

    private OperatorNode<SequenceOperator> convertPipe(Query_statementContext queryStatementContext, List<Pipeline_stepContext> nodes, Scope scope) {
        OperatorNode<SequenceOperator> result = convertQuery(queryStatementContext.getChild(0), scope.getRoot());
        for (Pipeline_stepContext step:nodes) {
            if (getParseTreeIndex(step.getChild(0)) == yqlplusParser.RULE_vespa_grouping) {
                result = OperatorNode.create(SequenceOperator.PIPE, result, List.of(),
                                             List.of(convertExpr(step.getChild(0), scope)));
            } else {
                List<String> name = readName(step.namespaced_name());
                List<OperatorNode<ExpressionOperator>> args = List.of();
                // LPAREN (argument[$in_select] (COMMA argument[$in_select])*) RPAREN
                if (step.getChildCount() > 1) {
                    ArgumentsContext arguments = step.arguments();
                    if (arguments.getChildCount() > 2) {
                        List<ArgumentContext> argumentContextList = arguments.argument();
                        args = new ArrayList<>(argumentContextList.size());
                        for (ArgumentContext argumentContext: argumentContextList) {
                            args.add(convertExpr(argumentContext.expression(), scope.getRoot()));

                        }
                    }
                }
                result = OperatorNode.create(SequenceOperator.PIPE, result, scope.resolvePath(name), args);
            }
        }
        return result;
    }

    private OperatorNode<SequenceOperator> convertQuery(ParseTree node, Scope scope) {
        if (node instanceof Select_statementContext) {
            return convertSelect(node, scope.getRoot());
        } else if (node instanceof Source_statementContext sourceStatementContext) { // for pipe
            return convertPipe(sourceStatementContext.query_statement(), sourceStatementContext.pipeline_step(), scope);
        } else {
            throw new IllegalArgumentException("Unexpected argument type to convertQueryStatement: " + node.toStringTree());
        }
    }

    private String assignAlias(String alias, ParserRuleContext node, Scope scope) {
        if (alias == null) {
            alias = "source";
        }
        
        if (node instanceof yqlplusParser.Alias_defContext) {
            // alias_def :   (AS? ID);
            ParseTree idChild = node;
            if (node.getChildCount() > 1) {
                idChild = node.getChild(1);
            }
            alias = idChild.getText();
            if (scope.isCursor(alias)) {
                throw new ProgramCompileException(toLocation(scope, idChild), "Source alias '%s' is already used", alias);
            }
            scope.defineDataSource(toLocation(scope, idChild), alias);
            return alias;
        } else {
            String candidate = alias;
            int c = 0;
            while (scope.isCursor(candidate)) {
                candidate = alias + (++c);
            }
            scope.defineDataSource(null, candidate);
            return alias;
        }
    }

    private OperatorNode<SequenceOperator> convertSource(ParserRuleContext sourceSpecNode, Scope scope) {
        // DataSources
       String alias;
       OperatorNode<SequenceOperator> result;
       ParserRuleContext dataSourceNode = sourceSpecNode;
       ParserRuleContext aliasContext = null;
       if (sourceSpecNode instanceof Source_specContext) {
           dataSourceNode = (ParserRuleContext)sourceSpecNode.getChild(0);
           if (sourceSpecNode.getChildCount() == 2) {
               aliasContext = (ParserRuleContext)sourceSpecNode.getChild(1);
           }
           if (dataSourceNode.getChild(0) instanceof Call_sourceContext ||
               dataSourceNode.getChild(0) instanceof  Sequence_sourceContext) {
               dataSourceNode = (ParserRuleContext)dataSourceNode.getChild(0);              
           } else { //source_statement
               dataSourceNode = (ParserRuleContext)dataSourceNode.getChild(1); 
           }
       }
        switch (getParseTreeIndex(dataSourceNode)) {
            case yqlplusParser.RULE_call_source -> {
                List<String> names = readName(dataSourceNode.getChild(Namespaced_nameContext.class, 0));
                alias = assignAlias(names.get(names.size() - 1), aliasContext, scope);
                List<OperatorNode<ExpressionOperator>> arguments = List.of();
                ArgumentsContext argumentsContext = dataSourceNode.getRuleContext(ArgumentsContext.class, 0);
                if (argumentsContext != null) {
                    List<ArgumentContext> argumentContexts = argumentsContext.argument();
                    arguments = new ArrayList<>(argumentContexts.size());
                    for (ArgumentContext argumentContext : argumentContexts) {
                        arguments.add(convertExpr(argumentContext, scope));
                    }
                }
                if (names.size() == 1 && scope.isVariable(names.get(0))) {
                    String ident = names.get(0);
                    if (arguments.size() > 0) {
                        throw new ProgramCompileException(toLocation(scope, argumentsContext), "Invalid call-with-arguments on local source '%s'", ident);
                    }
                    result = OperatorNode.create(toLocation(scope, dataSourceNode), SequenceOperator.EVALUATE, OperatorNode.create(toLocation(scope, dataSourceNode), ExpressionOperator.VARREF, ident));
                } else {
                    result = OperatorNode.create(toLocation(scope, dataSourceNode), SequenceOperator.SCAN, scope.resolvePath(names), arguments);
                }
            }
            case yqlplusParser.RULE_sequence_source -> {
                IdentContext identContext = dataSourceNode.getRuleContext(IdentContext.class, 0);
                String ident = identContext.getText();
                if (!scope.isVariable(ident)) {
                    throw new ProgramCompileException(toLocation(scope, identContext), "Unknown variable reference '%s'", ident);
                }
                alias = assignAlias(ident, aliasContext, scope);
                result = OperatorNode.create(toLocation(scope, dataSourceNode), SequenceOperator.EVALUATE, OperatorNode.create(toLocation(scope, dataSourceNode), ExpressionOperator.VARREF, ident));
            }
            case yqlplusParser.RULE_source_statement -> {
                alias = assignAlias(null, dataSourceNode, scope);
                result = convertQuery(dataSourceNode, scope);
            }
            default ->
                    throw new IllegalArgumentException("Unexpected argument type to convertSource: " + dataSourceNode.getText());
        }
        result.putAnnotation("alias", alias);
        return result;
    }

    private OperatorNode<StatementOperator> convertProgram(ParserRuleContext program,
                                                           yqlplusParser parser,
                                                           String programName) {
        Scope scope = new Scope(parser, programName);
        List<OperatorNode<StatementOperator>> stmts = new ArrayList<>();
        int output = 0;
        for (ParseTree node : program.children) {
            if (!(node instanceof ParserRuleContext ruleContext)) continue;
            if (ruleContext.getRuleIndex() != yqlplusParser.RULE_statement)
                throw new ProgramCompileException("Unknown program element: " + node.getText());

            // ^(STATEMENT_QUERY source_statement paged_clause? output_spec?)
            StatementContext statementContext = (StatementContext) ruleContext;
            Source_statementContext source_statement = statementContext.output_statement().source_statement();
            OperatorNode<SequenceOperator> query;
            if (source_statement.getChildCount() == 1) {
                query = convertQuery( source_statement.query_statement().getChild(0), scope);
            } else {
                query = convertQuery(source_statement, scope);
            }
            String variable = "result" + (++output);
            boolean isCountVariable = false;
            ParseTree outputStatement = node.getChild(0);
            Location location = toLocation(scope, outputStatement);
            for (int i = 1; i < outputStatement.getChildCount(); ++i) {
                ParseTree child = outputStatement.getChild(i);
                if ( getParseTreeIndex(child) != yqlplusParser.RULE_output_spec)
                    throw new ProgramCompileException( "Unknown statement attribute: " + child.toStringTree());

                Output_specContext outputSpecContext = (Output_specContext) child;
                variable = outputSpecContext.ident().getText();
                if (outputSpecContext.COUNT() != null) {
                    isCountVariable = true;
                }
            }
            scope.defineVariable(location, variable);
            stmts.add(OperatorNode.create(location, StatementOperator.EXECUTE, query, variable));
            stmts.add(OperatorNode.create(location, isCountVariable ? StatementOperator.COUNT:StatementOperator.OUTPUT, variable));
        }
        return OperatorNode.create(StatementOperator.PROGRAM, stmts);
    }

    private OperatorNode<SortOperator> convertSortKey(Orderby_fieldContext node, Scope scope) {
        TerminalNode descDef = node.DESC();
        OperatorNode<ExpressionOperator> exprNode = convertExpr(node.expression(), scope);
        if (descDef != null ) {
            return OperatorNode.create(toLocation(scope, descDef), SortOperator.DESC, exprNode);
        } else {
            return OperatorNode.create(toLocation(scope, node), SortOperator.ASC, exprNode);
        }
    }

    private ProjectionBuilder readProjection(List<Field_defContext> fieldDefs, Scope scope) {
        if (null == fieldDefs)
            throw new ProgramCompileException("Null fieldDefs");
        ProjectionBuilder proj = new ProjectionBuilder();
        for (Field_defContext rulenode : fieldDefs) {
            // FIELD
            // expression alias_def?
            OperatorNode<ExpressionOperator> expr = convertExpr(rulenode.getChild(0), scope);

            String aliasName = null;
            if (rulenode.getChildCount() > 1) {
                // ^(ALIAS ID)
                aliasName = rulenode.alias_def().IDENTIFIER().getText();
            }
            proj.addField(aliasName, expr);
            // no grammar for the other rule types at this time
        }
        return proj;
    }

    public static int getParseTreeIndex(ParseTree parseTree) {
        if (parseTree instanceof TerminalNode) {
            return ((TerminalNode)parseTree).getSymbol().getType();
        } else {
            return ((RuleNode)parseTree).getRuleContext().getRuleIndex();
        }
    }

	public OperatorNode<ExpressionOperator> convertExpr(ParseTree parseTree, Scope scope) {
        switch (getParseTreeIndex(parseTree)) {
            case yqlplusParser.RULE_vespa_grouping: {
                ParseTree firstChild = parseTree.getChild(0);
                if (getParseTreeIndex(firstChild) == yqlplusParser.RULE_annotation) {
                    ParseTree secondChild = parseTree.getChild(1);
                    OperatorNode<ExpressionOperator> annotation = convertExpr(((AnnotationContext) firstChild).map_expression(), scope);
                    OperatorNode<ExpressionOperator> expr = OperatorNode.create(toLocation(scope, secondChild),
                                                                                ExpressionOperator.VESPA_GROUPING, secondChild.getText());
                    List<String> names = annotation.getArgument(0);
                    List<OperatorNode<ExpressionOperator>> annotates = annotation.getArgument(1);
                    for (int i = 0; i < names.size(); ++i) {
                        expr.putAnnotation(names.get(i), readConstantExpression(annotates.get(i)));
                    }
                    return expr;
                } else {
                    return OperatorNode.create(toLocation(scope, firstChild), ExpressionOperator.VESPA_GROUPING,
                                               firstChild.getText());
                }
            }
            case yqlplusParser.RULE_null_operator:
                return OperatorNode.create(ExpressionOperator.NULL);
            case yqlplusParser.RULE_argument:
                return convertExpr(parseTree.getChild(0), scope);
            case yqlplusParser.RULE_fixed_or_parameter: {
                ParseTree firstChild = parseTree.getChild(0);
                if (getParseTreeIndex(firstChild) == yqlplusParser.INT) {
                    return OperatorNode.create(toLocation(scope, firstChild), ExpressionOperator.LITERAL, Integer.valueOf(firstChild.getText()));
                } else {
                    return convertExpr(firstChild, scope);
                }
            }
            case yqlplusParser.RULE_map_expression: {
                List<Property_name_and_valueContext> propertyList = ((Map_expressionContext)parseTree).property_name_and_value();
                List<String> names = new ArrayList<>(propertyList.size());
                List<OperatorNode<ExpressionOperator>> exprs = new ArrayList<>(propertyList.size());
                for (Property_name_and_valueContext child : propertyList) {
                    // : propertyName ':' expression[$expression::namespace] ->
                    // ^(PROPERTY propertyName expression)
                    names.add(StringUnescaper.unquote(child.getChild(0).getText()));
                    exprs.add(convertExpr(child.getChild(2), scope));
                }
                return OperatorNode.create(toLocation(scope, parseTree),ExpressionOperator.MAP, names, exprs);
            }
            case yqlplusParser.RULE_array_literal: {
                List<Constant_expressionContext> expressionList = ((Array_literalContext) parseTree).constant_expression();
                List<OperatorNode<ExpressionOperator>> values = new ArrayList<>(expressionList.size());
                for (Constant_expressionContext expr : expressionList) {
                    values.add(convertExpr(expr, scope));
                }
                return OperatorNode.create(toLocation(scope, expressionList.isEmpty()? parseTree:expressionList.get(0)), ExpressionOperator.ARRAY, values);
            }
            // dereferencedExpression: primaryExpression(indexref[in_select]| propertyref)*
            case yqlplusParser.RULE_dereferenced_expression: {
                Dereferenced_expressionContext dereferencedExpression = (Dereferenced_expressionContext) parseTree;
                Iterator<ParseTree> it = dereferencedExpression.children.iterator();
                OperatorNode<ExpressionOperator> result = convertExpr(it.next(), scope);
                while (it.hasNext()) {
                    ParseTree defTree = it.next();
                    if (getParseTreeIndex(defTree) == yqlplusParser.RULE_propertyref) {
                        // DOT nm=ID
                        result = OperatorNode.create(toLocation(scope, parseTree), ExpressionOperator.PROPREF, result, defTree.getChild(1).getText());
                    } else {
                        // indexref
                        result = OperatorNode.create(toLocation(scope, parseTree), ExpressionOperator.INDEX, result, convertExpr(defTree.getChild(1), scope));
                    }
                }
                return result;
            }
            case yqlplusParser.RULE_primary_expression: {
                // ^(CALL namespaced_name arguments)
                ParseTree firstChild = parseTree.getChild(0);
                switch (getParseTreeIndex(firstChild)) {
                    case yqlplusParser.RULE_fieldref: {
                        return convertExpr(firstChild, scope);
                    }
                    case yqlplusParser.RULE_call_expression: {
                        List<ArgumentContext> args = ((ArgumentsContext) firstChild.getChild(1)).argument();
                        List<OperatorNode<ExpressionOperator>> arguments = new ArrayList<>(args.size());
                        for (ArgumentContext argContext : args) {
                            arguments.add(convertExpr(argContext.expression(),scope));
                        }
                        return OperatorNode.create(toLocation(scope, parseTree), ExpressionOperator.CALL, scope.resolvePath(readName((Namespaced_nameContext) firstChild.getChild(0))), arguments);
                    }
                    case yqlplusParser.RULE_constant_expression:
                        return convertExpr(firstChild, scope);

                    case yqlplusParser.LPAREN:
                        return convertExpr(parseTree.getChild(1), scope);
                }
                break;
            }
            case yqlplusParser.RULE_parameter: {
                // external variable reference
                ParserRuleContext parameterContext = (ParserRuleContext) parseTree;
                IdentContext identContext = parameterContext.getRuleContext(IdentContext.class, 0);
                return OperatorNode.create(toLocation(scope, identContext), ExpressionOperator.VARREF, identContext.getText());
            }
            case yqlplusParser.RULE_annotate_expression: {
                //annotation logicalORExpression
                AnnotationContext annotateExpressionContext = ((Annotate_expressionContext)parseTree).annotation();
                OperatorNode<ExpressionOperator> annotation = convertExpr(annotateExpressionContext.map_expression(), scope);
                OperatorNode<ExpressionOperator> expr = convertExpr(parseTree.getChild(1), scope);
                List<String> names = annotation.getArgument(0);
                List<OperatorNode<ExpressionOperator>> annotates = annotation.getArgument(1);
                for (int i = 0; i < names.size(); ++i) {
                    expr.putAnnotation(names.get(i), readConstantExpression(annotates.get(i)));
                }
                return expr;
            }
            case yqlplusParser.RULE_expression: {
                return convertExpr(parseTree.getChild(0), scope);
            }
            case yqlplusParser.RULE_logical_AND_expression:
                Logical_AND_expressionContext andExpressionContext = (Logical_AND_expressionContext) parseTree;
                return readConjOp(ExpressionOperator.AND, andExpressionContext.equality_expression(), scope);
            case yqlplusParser.RULE_logical_OR_expression: {
                int childCount = parseTree.getChildCount();
                Logical_OR_expressionContext logicalORExpressionContext = (Logical_OR_expressionContext) parseTree;
                if (childCount > 1) {
                    return readConjOrOp(ExpressionOperator.OR, logicalORExpressionContext, scope);
                } else {
                    List<Equality_expressionContext> equalityExpressionList = ((Logical_AND_expressionContext) parseTree.getChild(0)).equality_expression();
                    if (equalityExpressionList.size() > 1) {
                        return readConjOp(ExpressionOperator.AND, equalityExpressionList, scope);
                    } else {
                        return convertExpr(equalityExpressionList.get(0), scope);
                    }
                }
            }
            case yqlplusParser.RULE_equality_expression: {
                Equality_expressionContext equalityExpression = (Equality_expressionContext) parseTree;
                Relational_expressionContext relationalExpressionContext = equalityExpression.relational_expression(0);
                OperatorNode<ExpressionOperator> expr = convertExpr(relationalExpressionContext, scope);
                In_not_in_targetContext inNotInTarget = equalityExpression.in_not_in_target();
                int childCount = equalityExpression.getChildCount();
                if (childCount == 1) {
                    return expr;
                }
                if (inNotInTarget != null) {
                    Literal_listContext literalListContext = inNotInTarget.literal_list();
                    boolean isIN = equalityExpression.IN() != null;
                    if (literalListContext == null) {
                        Select_statementContext selectStatementContext = inNotInTarget.select_statement();
                        OperatorNode<SequenceOperator> query = convertQuery(selectStatementContext, scope);
                        return OperatorNode.create(expr.getLocation(),isIN ? ExpressionOperator.IN_QUERY: ExpressionOperator.NOT_IN_QUERY, expr, query);
                    } else {
                        // we need to identify the type of the target; if it's a
                        // scalar we need to wrap it in a CREATE_ARRAY
                        // if it's already a CREATE ARRAY then it's fine, otherwise
                        // we need to know the variable type
                        // return readBinOp(node.getType() == yqlplusParser.IN ?
                        // ExpressionOperator.IN : ExpressionOperator.NOT_IN, node,
                        // scope);
                        return readBinOp(isIN ? ExpressionOperator.IN: ExpressionOperator.NOT_IN, equalityExpression.getChild(0), literalListContext, scope);
                    }

                } else {
                    ParseTree firstChild = equalityExpression.getChild(1);
                    if (equalityExpression.getChildCount() == 2) {
                        switch (getParseTreeIndex(firstChild)) {
                            case yqlplusParser.IS_NULL:
                                return readUnOp(ExpressionOperator.IS_NULL, relationalExpressionContext, scope);
                            case yqlplusParser.IS_NOT_NULL:
                                return readUnOp(ExpressionOperator.IS_NOT_NULL, relationalExpressionContext, scope);
                        }
                    } else {
                        switch (getParseTreeIndex(firstChild.getChild(0))) {
                            case yqlplusParser.EQ:
                                return readBinOp(ExpressionOperator.EQ, equalityExpression.getChild(0), equalityExpression.getChild(2), scope);
                            case yqlplusParser.NEQ:
                                return readBinOp(ExpressionOperator.NEQ, equalityExpression.getChild(0), equalityExpression.getChild(2), scope);
                            case yqlplusParser.LIKE:
                                return readBinOp(ExpressionOperator.LIKE, equalityExpression.getChild(0), equalityExpression.getChild(2), scope);
                            case yqlplusParser.NOTLIKE:
                                return readBinOp(ExpressionOperator.NOT_LIKE, equalityExpression.getChild(0), equalityExpression.getChild(2), scope);
                            case yqlplusParser.MATCHES:
                                return readBinOp(ExpressionOperator.MATCHES, equalityExpression.getChild(0), equalityExpression.getChild(2), scope);
                            case yqlplusParser.NOTMATCHES:
                                return readBinOp(ExpressionOperator.NOT_MATCHES, equalityExpression.getChild(0), equalityExpression.getChild(2), scope);
                            case yqlplusParser.CONTAINS:
                                return readBinOp(ExpressionOperator.CONTAINS, equalityExpression.getChild(0), equalityExpression.getChild(2), scope);
                        }
                    }

                }
                break;
            }
            case yqlplusParser.RULE_relational_expression: {
                Relational_expressionContext relationalExpressionContext = (Relational_expressionContext) parseTree;
                Relational_opContext opContext = relationalExpressionContext.relational_op();
                if (opContext != null) {
                    switch (getParseTreeIndex(relationalExpressionContext.relational_op().getChild(0))) {
                        case yqlplusParser.LT:
                            return readBinOp(ExpressionOperator.LT, parseTree, scope);
                        case yqlplusParser.LTEQ:
                            return readBinOp(ExpressionOperator.LTEQ, parseTree, scope);
                        case yqlplusParser.GT:
                            return readBinOp(ExpressionOperator.GT, parseTree, scope);
                        case yqlplusParser.GTEQ:
                            return readBinOp(ExpressionOperator.GTEQ, parseTree, scope);
                    }
                } else {
                    return convertExpr(relationalExpressionContext.additive_expression(0), scope);
                }
            }
            break;
            case yqlplusParser.RULE_additive_expression:
            case yqlplusParser.RULE_multiplicative_expression: {
                if (parseTree.getChildCount() > 1) {
                    String opStr = parseTree.getChild(1).getText();
                    switch (opStr) {
                        case "+":
                            return readBinOp(ExpressionOperator.ADD, parseTree, scope);
                        case "-":
                            return readBinOp(ExpressionOperator.SUB, parseTree, scope);
                        case "/":
                            return readBinOp(ExpressionOperator.DIV, parseTree, scope);
                        case "*":
                            return readBinOp(ExpressionOperator.MULT, parseTree, scope);
                        case "%":
                            return readBinOp(ExpressionOperator.MOD, parseTree, scope);
                        default:
                            if (parseTree.getChild(0) instanceof Unary_expressionContext) {
                                return convertExpr(parseTree.getChild(0), scope);
                            } else {
                                throw new ProgramCompileException(toLocation(scope, parseTree), "Unknown expression type: " + parseTree.toStringTree());
                            }
                    }
                } else {
                    if (parseTree.getChild(0) instanceof Unary_expressionContext) {
                        return convertExpr(parseTree.getChild(0), scope);
                    } else if (parseTree.getChild(0) instanceof Multiplicative_expressionContext) {
                        return convertExpr(parseTree.getChild(0), scope);
                    } else {
                        throw new ProgramCompileException(toLocation(scope, parseTree), "Unknown expression type: " + parseTree.getText());
                    }
                }
            }
            case yqlplusParser.RULE_unary_expression: {
                if (1 == parseTree.getChildCount()) {
                    return convertExpr(parseTree.getChild(0), scope);
                } else if (2 == parseTree.getChildCount()) {
                    if ("-".equals(parseTree.getChild(0).getText())) {
                        return readUnOp(ExpressionOperator.NEGATE, parseTree, scope);
                    } else if ("!".equals(parseTree.getChild(0).getText())) {
                        return readUnOp(ExpressionOperator.NOT, parseTree, scope);
                    }
                    throw new ProgramCompileException(toLocation(scope, parseTree),"Unknown unary operator " + parseTree.getText());
                } else {
                    throw new ProgramCompileException(toLocation(scope, parseTree),"Unknown child count " + parseTree.getChildCount() + " of " + parseTree.getText());
                }
            }
            case yqlplusParser.RULE_fieldref: {
                // all in-scope data sources should be defined in scope
                // the 'first' field in a namespaced reference must be:
                // - a field name if (and only if) there is exactly one data source
                // in scope OR
                // - an alias name, which will be followed by a field name
                // ^(FIELDREF<FieldReference>[$expression::namespace]
                // namespaced_name)
                List<String> path = readName((Namespaced_nameContext) parseTree.getChild(0));
                Location loc = toLocation(scope, parseTree.getChild(0));
                String alias = path.get(0);
                OperatorNode<ExpressionOperator> result;
                int start;
                if (scope.isCursor(alias)) {
                    if (path.size() > 1) {
                        result = OperatorNode.create(loc, ExpressionOperator.READ_FIELD, alias, path.get(1));
                        start = 2;
                    } else {
                        result = OperatorNode.create(loc, ExpressionOperator.READ_RECORD, alias);
                        start = 1;
                    }
                } else if (scope.getCursors().size() == 1) {
                    alias = scope.getCursors().iterator().next();
                    result = OperatorNode.create(loc, ExpressionOperator.READ_FIELD, alias, path.get(0));
                    start = 1;
                } else {
                    throw new ProgramCompileException(loc, "Unknown field or alias '%s'", alias);
                }
                for (int idx = start; idx < path.size(); ++idx) {
                    result = OperatorNode.create(loc, ExpressionOperator.PROPREF, result, path.get(idx));
                }
                return result;
            }
            case yqlplusParser.RULE_scalar_literal:
                return OperatorNode.create(toLocation(scope, parseTree), ExpressionOperator.LITERAL, convertLiteral((Scalar_literalContext) parseTree));
            case yqlplusParser.RULE_constant_expression:
                return convertExpr(parseTree.getChild(0), scope);
            case yqlplusParser.RULE_literal_list:
                if (getParseTreeIndex(parseTree.getChild(1)) == yqlplusParser.RULE_array_parameter) {
                    return convertExpr(parseTree.getChild(1), scope);
                } else {
                    List<Literal_elementContext> elements = ((Literal_listContext) parseTree).literal_element();
                    ParseTree firldElement = elements.get(0).getChild(0);
                    if (elements.size() == 1 && scope.getParser().isArrayParameter(firldElement)) {
                        return convertExpr(firldElement, scope);
                    } else {
                        List<OperatorNode<ExpressionOperator>> values = new ArrayList<>(elements.size());
                        for (Literal_elementContext child : elements) {
                            values.add(convertExpr(child.getChild(0), scope));
                        }
                        return OperatorNode.create(toLocation(scope, elements.get(0)),ExpressionOperator.ARRAY, values);
                    }
                }
        }
        throw new ProgramCompileException(toLocation(scope, parseTree),
                                          "Unknown expression type: " + parseTree.getText());
	}

    public Object convertLiteral(Scalar_literalContext literal) {
        int parseTreeIndex = getParseTreeIndex(literal.getChild(0));
        String text = literal.getChild(0).getText();
        switch(parseTreeIndex) {
            case yqlplusParser.INT:
                long as_long = Long.parseLong(text);
                int as_int = (int)as_long;
                if (as_int == as_long) {
                    return as_int;
                } else {
                    return as_long;
                }
            case yqlplusParser.FLOAT:
                return Double.valueOf(text);
            case yqlplusParser.STRING:
                return StringUnescaper.unquote(text);
            case yqlplusParser.TRUE:
                return true;
            case yqlplusParser.FALSE:
                return false;
            case yqlplusParser.LONG_INT:
                return Long.parseLong(text.substring(0, text.length()-1));
            default:
                throw new ProgramCompileException("Unknown literal type " + text);
        }
    }

    private Object readConstantExpression(OperatorNode<ExpressionOperator> node) {
        switch (node.getOperator()) {
            case LITERAL:
                return node.getArgument(0);
            case MAP: {
                ImmutableMap.Builder<String, Object> map = ImmutableMap.builder();
                List<String> names = node.getArgument(0);
                List<OperatorNode<ExpressionOperator>> exprs = node.getArgument(1);
                for (int i = 0; i < names.size(); ++i) {
                    map.put(names.get(i), readConstantExpression(exprs.get(i)));
                }
                return map.build();
            }
            case ARRAY: {
                List<OperatorNode<ExpressionOperator>> exprs = node.getArgument(0);
                return exprs.stream().map(expr -> readConstantExpression(expr)).toList();
            }
            case VARREF: {
                return node; // must be dereferenced in YqlParser when we have userQuery
            }
            default:
                throw new ProgramCompileException(node.getLocation(), "Internal error: Unknown constant expression type: " + node.getOperator());
        }
    }

    private OperatorNode<ExpressionOperator> readBinOp(ExpressionOperator op, ParseTree node, Scope scope) {
        assert node.getChildCount() == 3;
        return OperatorNode.create(op, convertExpr(node.getChild(0), scope), convertExpr(node.getChild(2), scope));
    }

    private OperatorNode<ExpressionOperator> readBinOp(ExpressionOperator op, ParseTree operand1, ParseTree operand2, Scope scope) {
        return OperatorNode.create(op, convertExpr(operand1, scope), convertExpr(operand2, scope));
    }

    private OperatorNode<ExpressionOperator> readConjOp(ExpressionOperator op, List<Equality_expressionContext> nodes, Scope scope) {
        List<OperatorNode<ExpressionOperator>> arguments = new ArrayList<>(nodes.size());
        for (ParseTree child : nodes) {
            arguments.add(convertExpr(child, scope));
        }
        return OperatorNode.create(op, arguments);
    }

    private OperatorNode<ExpressionOperator> readConjOrOp(ExpressionOperator op, Logical_OR_expressionContext node, Scope scope) {
        List<Logical_AND_expressionContext> andExpressionList = node.logical_AND_expression();
        List<OperatorNode<ExpressionOperator>> arguments = new ArrayList<>(andExpressionList.size());
        for (Logical_AND_expressionContext child : andExpressionList) {
         	List<Equality_expressionContext> equalities = child.equality_expression();
         	if (equalities.size() == 1) {
         		arguments.add(convertExpr(equalities.get(0), scope));
         	} else {
         		List<OperatorNode<ExpressionOperator>> andArguments = new ArrayList<>(equalities.size());
         		for (Equality_expressionContext subTreeChild:equalities) {
         				andArguments.add(convertExpr(subTreeChild, scope));
         		}
         		arguments.add(OperatorNode.create(ExpressionOperator.AND, andArguments));
         	}

        }
        return OperatorNode.create(op, arguments);
    }

    // (IS_NULL | IS_NOT_NULL)
    // unaryExpression
    private OperatorNode<ExpressionOperator> readUnOp(ExpressionOperator op, ParseTree node, Scope scope) {
        assert (node instanceof TerminalNode) || (node.getChildCount() == 1) || (node instanceof Unary_expressionContext);
        if (node instanceof TerminalNode) {
            return OperatorNode.create(op, convertExpr(node, scope));
        } else if (node.getChildCount() == 1) {
            return OperatorNode.create(op, convertExpr(node.getChild(0), scope));
        } else {
            return OperatorNode.create(op, convertExpr(node.getChild(1), scope));
        }
    }

    /**
     * Scans the given node for READ_FIELD expressions.
     *
     * TODO: Search recursively and consider additional operators
     *
     * @param in the node to scan
     * @return list of READ_FIELD expressions
     */
    private List<OperatorNode<ExpressionOperator>> getReadFieldExpressions(OperatorNode<ExpressionOperator> in) {
        List<OperatorNode<ExpressionOperator>> readFieldList = new ArrayList<>();
        switch (in.getOperator()) {
            case READ_FIELD -> readFieldList.add(in);
            case CALL -> {
                List<OperatorNode<ExpressionOperator>> callArgs = in.getArgument(1);
                for (OperatorNode<ExpressionOperator> callArg : callArgs) {
                    if (callArg.getOperator() == ExpressionOperator.READ_FIELD) {
                        readFieldList.add(callArg);
                    }
                }
            }
        }
        return readFieldList;
    }

}
