// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
grammar yqlplus;

options {
  superClass = ParserBase;
  language = Java;
}

@header {
  import java.util.Stack;
  import com.yahoo.search.yql.*;
}

@parser::members {
	protected static class expression_scope {
        boolean in_select;
    }
    protected Stack<expression_scope> expression_stack = new Stack();    
}

// tokens for command syntax
  CREATE : 'create';
  SELECT : 'select';
  INSERT : 'insert';
  UPDATE : 'update';
  SET : 'set';
  VIEW : 'view';
  TABLE  : 'table';
  DELETE : 'delete';
  INTO   : 'into';
  VALUES : 'values';
  IMPORT : 'import';
  NEXT : 'next';
  PAGED : 'paged';
  FALLBACK : 'fallback';
  IMPORT_FROM :;

  LIMIT : 'limit';
  OFFSET : 'offset';
  WHERE : 'where';
  ORDERBY : 'order by';
  DESC : 'desc';
  ASC :;
  FROM : 'from';
  SOURCES : 'sources';
  AS : 'as';
  MERGE : 'merge';
  LEFT : 'left';
  JOIN : 'join';
  
  ON : 'on';
  COMMA : ',';
  OUTPUT : 'output';
  COUNT : 'count';
  RETURNING : 'returning';
  APPLY : 'apply';
  CAST : 'cast';

  BEGIN : 'begin';
  END : 'end';

  // type-related
  TYPE_BYTE : 'byte';
  TYPE_INT16 : 'int16';
  TYPE_INT32 : 'int32';
  TYPE_INT64 : 'int64';
  TYPE_STRING : 'string';
  TYPE_DOUBLE : 'double';
  TYPE_TIMESTAMP : 'timestamp';
  TYPE_BOOLEAN : 'boolean';
  TYPE_ARRAY : 'array';
  TYPE_MAP : 'map';

  // READ_FIELD;

  // token literals
  TRUE : 'true';
  FALSE : 'false';

  // brackets and other tokens in literals

  LPAREN : '(';
  RPAREN : ')';
  LBRACKET : '[';
  RBRACKET : ']';
  LBRACE : '{';
  RBRACE : '}';
  COLON : ':';
  PIPE : '|';

  // operators
  AND : 'and';
  OR : 'or';
  NOT_IN : 'not in';
  IN : 'in';
  QUERY_ARRAY :;

  LT : '<';
  GT : '>';
  LTEQ : '<=';
  GTEQ : '>=';
  NEQ : '!=';
  STAR : '*';
  EQ : '=';
  LIKE : 'like';
  CONTAINS : 'contains';
  NOTLIKE : 'not like';
  MATCHES : 'matches';
  NOTMATCHES : 'not matches';

  // effectively unary operators
  IS_NULL : 'is null';
  IS_NOT_NULL : 'is not null';

  // dereference
  DOT : '.';
  AT : '@';

  // quotes
  SQ : '\'';
  DQ : '"';

  // statement delimiter
  SEMI : ';';

  PROGRAM : 'program';
  TIMEOUT : 'timeout';

//following node names seems only used for root node name 
//not exactly matching anything, should we still keep it?
// Follow tree name are not no longer used
//comment out maybe remove
//  ARGUMENT : ;
//  TYPE : ;
//  NAME : ;
//  DEFAULT :;
//
//  ANNOTATE : ;
//
//  PROJECT :;
//  FILTER :;
//
//  PROPERTY :;
//  LITERAL :;
//  PARAMETER :;
//  SCALAR_LITERAL :;
//  MAP_LITERAL :;
// // ARRAY_LITERAL :;
//  FIELD :;
// //* FIELD_ASSIGNMENT;
//  ALL_SOURCE :;
//  MULTI_SOURCE :;
//  EXPRESSION_SOURCE :;
//  SEQUENCE_SOURCE :;
//  CALL_SOURCE :;
//  PIPELINE_STEP :;
//  AUTO_ALIAS :;
// // BINOP_ADD :;
// // BINOP_SUB :;
//  BINOP_MULT :;
//  BINOP_DIV :;  
//  BINOP_MOD :;
//
//  UNOP_MINUS :;
//  UNOP_NOT :;
//  
//  STATEMENT_SELECTVAR :;
//  STATEMENT_QUERY :;
//  
//  FIELDREF :;
//  PATH :;
//
//  CALL :;
//
// // INDEXREF :;
//  PROPERTYREF :;
//  
//  LEFT_JOIN :;
//  RIGHT_JOIN :;
////  FULL_JOIN;
//
//  ALIAS :;
//
//  INSERT_VALUES :;
//  UPDATE_VALUES :;
//
//  NO_ARGUMENTS :;



/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

ID  :	('a'..'z'|'A'..'Z'|'_') ('a'..'z'|'A'..'Z'|'0'..'9'|'_'|':')*
    ;

LONG_INT :	'-'?'0'..'9'+ ('L'|'l')
    ;

INT :	'-'?'0'..'9'+
    ;

FLOAT
    :   ('-')?('0'..'9')+ '.' ('0'..'9')* EXPONENT?
    |   ('-')?'.' ('0'..'9')+ EXPONENT?
    |   ('-')?('0'..'9')+ EXPONENT
    ;

fragment
EXPONENT : ('e'|'E') ('+'|'-')? ('0'..'9')+ ;


fragment
DIGIT : '0'..'9'
    ;

fragment
LETTER  : 'a'..'z'
    	| 'A'..'Z'
    	;

//STRING  :  DQ ( ESC_SEQ | ~('\\'| DQ) )* DQ
//        |  SQ ( ESC_SEQ | ~('\\' | SQ) )* SQ
STRING  :  '"' ( ESC_SEQ | ~('\\'| '"') )* '"'
        |  '\'' ( ESC_SEQ | ~('\\' | '\'') )* '\''
	;

/////////////////////////////
fragment
HEX_DIGIT : ('0'..'9'|'a'..'f'|'A'..'F') ;
	

fragment
ESC_SEQ
    :   '\\' ('b'|'t'|'n'|'f'|'r'|'\"'|'\''|'\\'|'/')
    |   UNICODE_ESC
 //   |   OCTAL_ESC
    ;

/*
fragment
OCTAL_ESC
    :   '\\' ('0'..'3') ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7')
    ;
*/

fragment
UNICODE_ESC
    :   '\\' 'u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT
    ;

WS  :   ( ' '
        | '\t'
        | '\r'
        | '\n'
  //      ) {$channel=HIDDEN;}
  	) -> channel(HIDDEN)
    ;

//COMMENT
//    :   ('//') ~('\n'|'\r')* '\r'? '\n'? {$channel=HIDDEN;}
//    |   '/*' ( options {greedy=false;} : . )* '*/' {$channel=HIDDEN;}
//    ;	
COMMENT
    :  ( ('//') ~('\n'|'\r')* '\r'? '\n'? 
  //  |   '/*' ( options {greedy=false;} : . )* '*/' 
    | '/*' .*? '*/'
    )
    -> channel(HIDDEN)
    ;

VESPA_GROUPING
    : ('all' | 'each') WS* VESPA_GROUPING_ARG WS*
        ('as' WS* VESPA_GROUPING_ARG WS*)?
        ('where' WS* VESPA_GROUPING_ARG)?
    ;

fragment
VESPA_GROUPING_ARG
    : ('(' | '[' | '<')
        ( ~('(' | '[' | '<' | ')' | ']' | '>') | VESPA_GROUPING_ARG )*
        (')' | ']' | '>')
    ;

/*------------------------------------LPAREN! select_statement RPAREN!------------------------------
 * PARSER RULES
 *------------------------------------------------------------------*/

ident
   : keyword_as_ident //{addChild(new TerminalNodeImpl(keyword_as_ident.getText()));}
   		//{return ID<IDNode>[$keyword_as_ident.text];}
   | ID
   ;

keyword_as_ident
   : SELECT | TABLE | DELETE | INTO | VALUES | LIMIT | OFFSET | WHERE | 'order' | 'by' | DESC | MERGE | LEFT | JOIN
   | ON | OUTPUT | COUNT | BEGIN | END | APPLY | TYPE_BYTE | TYPE_INT16 | TYPE_INT32 | TYPE_INT64 | TYPE_BOOLEAN | TYPE_TIMESTAMP | TYPE_DOUBLE | TYPE_STRING | TYPE_ARRAY | TYPE_MAP
   | VIEW | CREATE | IMPORT | PROGRAM | NEXT | PAGED | SOURCES | SET | MATCHES
   ;

//program : params? (import_statement SEMI)* (ddl SEMI)* (statement SEMI)* EOF
//              -> ^(PROGRAM params? import_statement* ddl* statement*)
//	;
program : params? (import_statement SEMI)* (ddl SEMI)* (statement SEMI)* EOF
	;

//params
//     : PROGRAM! LPAREN! program_arglist? RPAREN! SEMI!
//     ;
params
     : PROGRAM LPAREN program_arglist? RPAREN SEMI
     ;

//import_statement
//      : IMPORT moduleName AS moduleId -> ^(IMPORT moduleName moduleId)
//      | IMPORT moduleId               -> ^(IMPORT moduleId)
//      | FROM moduleName IMPORT import_list -> ^(IMPORT_FROM moduleName import_list+)
//      ;
import_statement
      : IMPORT moduleName AS moduleId
      | IMPORT moduleId               
      | FROM moduleName IMPORT import_list
      ;

//import_list
//      : moduleId (',' moduleId)* -> moduleId+
//      ;
import_list
      : moduleId (',' moduleId)*
      ;

//moduleId
//    :  ID  -> STRING<LiteralNode>[$ID, $ID.text]
//    ;
moduleId
    :  ID
    ;
    
moduleName
	: literalString
	| namespaced_name
	;

ddl
      : view
      ;

//view  : CREATE VIEW ID AS source_statement -> ^(VIEW ID source_statement)
//      ;
view  : CREATE VIEW ID AS source_statement
      ;


//program_arglist
//      : procedure_argument (',' procedure_argument)* ->  procedure_argument+
//      ;
program_arglist
      : procedure_argument (',' procedure_argument)*
      ;

//procedure_argument
//      : AT (ident TYPE_ARRAY LT typename GTEQ (expression[false])? ) {registerParameter($ident.tree, $typename.tree);} -> ^(ARGUMENT ident typename expression?)
//      | AT (ident typename ('=' expression[false])? ) {registerParameter($ident.tree, $typename.tree);} -> ^(ARGUMENT ident typename expression?)
//      ;
procedure_argument
      :
      	AT (ident TYPE_ARRAY LT typename GTEQ (expression[false])? ) {registerParameter($ident.start.getText(), $typename.start.getText());}
      | AT (ident typename ('=' expression[false])? ) {registerParameter($ident.start.getText(), $typename.start.getText());}
      ;

statement
      : output_statement
	  | selectvar_statement
	  | next_statement
	  ;

//output_statement
//      : source_statement paged_clause? output_spec? -> ^(STATEMENT_QUERY source_statement paged_clause? output_spec?)
//      ;
output_statement
      : source_statement paged_clause? output_spec?
      ;

//paged_clause
//      : PAGED^ fixed_or_parameter
//      ;
paged_clause
      : PAGED fixed_or_parameter
      ;

//next_statement
//	  : NEXT^ literalString OUTPUT! AS! ident
//	  ;
next_statement
	  : NEXT literalString OUTPUT AS ident
	  ;

//source_statement
//      : query_statement (PIPE pipeline_step)+ -> ^(PIPE query_statement pipeline_step+)
//      | query_statement
//      ;
source_statement
      : query_statement (PIPE pipeline_step)*
      ;


//pipeline_step
//      : namespaced_name arguments[false]? -> ^(PIPELINE_STEP namespaced_name arguments?)
//      ;
pipeline_step
      : namespaced_name arguments[false]?
      | vespa_grouping
      ;

vespa_grouping
      : VESPA_GROUPING
      | annotation VESPA_GROUPING
      ;

//selectvar_statement
//      : CREATE ('temp' | 'temporary') TABLE ident AS LPAREN source_statement RPAREN -> ^(STATEMENT_SELECTVAR ident source_statement)
//      ;
selectvar_statement
      : CREATE ('temp' | 'temporary') TABLE ident AS LPAREN source_statement RPAREN
      ;


typename
      : TYPE_BYTE | TYPE_INT16 | TYPE_INT32 | TYPE_INT64 | TYPE_STRING | TYPE_BOOLEAN | TYPE_TIMESTAMP
      | arrayType | mapType | TYPE_DOUBLE
      ;

//arrayType
//      : TYPE_ARRAY^ LT! typename GT!
//      ;
arrayType
      : TYPE_ARRAY LT typename GT
      ;

//mapType
//      : TYPE_MAP^ LT! typename GT!
//      ;
mapType
      : TYPE_MAP LT typename GT
      ;

//output_spec
//      : (OUTPUT^ AS! ident)
//      | (OUTPUT! COUNT^ AS! ident)
//      ;
output_spec
      : (OUTPUT AS ident)
      | (OUTPUT COUNT AS ident)
      ;


query_statement
    : merge_statement
    | select_statement
    | insert_statement
    | delete_statement
    | update_statement
    ;

// This does not use the UNION / UNION ALL from SQL because the semantics are different than SQL UNION
//   - no set operation is implied (no DISTINCT)
//   - CQL resultsets may be heterogeneous (rows may have heterogenous types)
//merge_statement
//	:	merge_component (MERGE merge_component)+ -> ^(MERGE merge_component+)
//	;
merge_statement
	:	merge_component (MERGE merge_component)+
	;

merge_component
	: select_statement
	| LPAREN source_statement RPAREN
	;

//select_statement
//	:	SELECT^ select_field_spec select_source where? orderby? limit? offset? timeout? fallback?
//    ;
select_statement
	:	SELECT select_field_spec select_source? where? orderby? limit? offset? timeout? fallback?
//    |   SELECT select_field_spec where? orderby? limit? offset? timeout? fallback?
    ;

//select_field_spec
//	:	field_def (COMMA field_def)* -> ^(PROJECT field_def+)
//	| 	STAR!
//	;
select_field_spec
	:	project_spec
	| 	STAR
	;
	
project_spec
	: field_def (COMMA  field_def)*
	;

//fallback
//    : FALLBACK^ select_statement
//    ;
fallback
    : FALLBACK select_statement
    ;

//timeout
//	:  TIMEOUT^ fixed_or_parameter
//	;
timeout
	:  TIMEOUT fixed_or_parameter
	;

//select_source
//    :   FROM SOURCES STAR -> ALL_SOURCE
//    |   FROM SOURCES source_list -> ^(MULTI_SOURCE source_list)
//	|	FROM^ source_spec join_expr*
//	|   -> EXPRESSION_SOURCE
//	;
	
select_source
    :   select_source_all
    |   select_source_multi
	|	select_source_join
	;

select_source_all
	:   FROM SOURCES STAR
	;

select_source_multi
 	: 	FROM SOURCES source_list
 	;
 	
select_source_join
	:	FROM source_spec join_expr*
	;

//source_list
//    : namespaced_name (COMMA namespaced_name)* -> namespaced_name+
//    ;
source_list
    : namespaced_name (COMMA namespaced_name )*
    ;

//join_expr
//    : (join_spec^ source_spec ON! joinExpression)
//    ;
join_expr
    : (join_spec source_spec ON joinExpression)
    ;

//join_spec
//	: LEFT JOIN     -> LEFT_JOIN
//	| 'inner'? JOIN -> JOIN
//	;
join_spec
	: LEFT JOIN
	| 'inner'? JOIN
	;

//source_spec
//	:	( data_source (alias_def { ($data_source.tree).addChild($alias_def.tree); } )? )  -> data_source
//	;
source_spec
	:	( data_source (alias_def { ($data_source.ctx).addChild($alias_def.ctx); })? )
	;

//alias_def
//	:	(AS? ID)   -> ^(ALIAS ID)
//	;
alias_def
	:	(AS? ID)
	;

//data_source
//	:	namespaced_name arguments[true]? -> ^(CALL_SOURCE namespaced_name arguments?)
//	|	LPAREN! source_statement RPAREN!
//	|   AT ident -> ^(SEQUENCE_SOURCE ident)
//	;

data_source
	:	call_source
	|	LPAREN source_statement RPAREN
	|   sequence_source
	;
	
call_source
	: namespaced_name arguments[true]?
	;
	
sequence_source
	: AT ident
	;

//namespaced_name
//    :   (ident (DOT ident)* (DOT STAR)?) -> ^(PATH ident+ STAR?)
//    ;
namespaced_name
    :   (ident (DOT ident)* (DOT STAR)?)
    ;

//orderby
//	:	ORDERBY orderby_fields -> ^(ORDERBY orderby_fields)
//	;
orderby
	:	ORDERBY orderby_fields
	;
	
//orderby_fields
//	:	orderby_field (COMMA orderby_field)* -> orderby_field+
//	;
orderby_fields
	:	orderby_field (COMMA orderby_field)*
	;	
	
//orderby_field
//	:   expression[true] DESC     -> ^(DESC expression)
//	|	expression[true] ('asc')? -> ^(ASC expression)
//	;
orderby_field
	:   expression[true] DESC
	|	expression[true] ('asc')?
	;	

//limit
//	:	LIMIT fixed_or_parameter -> ^(LIMIT fixed_or_parameter)
//	;
limit
	:	LIMIT fixed_or_parameter
	;

//offset
//    : OFFSET fixed_or_parameter -> ^(OFFSET fixed_or_parameter)
//	;
offset
    : OFFSET fixed_or_parameter
	;


//where
//	: WHERE expression[true] -> ^(FILTER expression)
//	;
where
	: WHERE expression[true]
	;

//field_def
//	:	expression[true] alias_def? -> ^(FIELD expression alias_def?)
//	;
field_def
	: expression[true] alias_def?
	;

// Hive doesn't have syntax for creating maps - this is an extension
//mapExpression
//    : LBRACE i+=propertyNameAndValue? (COMMA i+=propertyNameAndValue)* RBRACE -> ^(MAP_LITERAL $i*)
//    ;
mapExpression
    : LBRACE propertyNameAndValue? (COMMA propertyNameAndValue)* RBRACE
    ;

//constantMapExpression
//    : LBRACE i+=constantPropertyNameAndValue? (COMMA i+=constantPropertyNameAndValue)* RBRACE -> ^(MAP_LITERAL $i+)
//    ;
constantMapExpression
    : LBRACE constantPropertyNameAndValue? (COMMA constantPropertyNameAndValue)* RBRACE
    ;

//arguments[boolean in_select]
//	:  LPAREN RPAREN                                                      -> NO_ARGUMENTS
//	|  LPAREN (argument[$in_select] (COMMA argument[$in_select])*) RPAREN -> argument+
//	;
arguments[boolean in_select]
	:  LPAREN RPAREN                                                    
	|  LPAREN (argument[$in_select] (COMMA argument[$in_select])*) RPAREN
	;

argument[boolean in_select]
    : expression[$in_select]
    ;

// -------- join expressions ------------

// Limit expression syntax for joins
// for now, a single equality test and one field from each source
// this makes it easier for the prototype to be sure it can generate code
// effectively this means it can always turn the join into a query to one source, collecting all of the
// keys from the results, and then a query to the other source
// (or querying the other source inline)
// do not support map or index references
// this can become more general as the engine gets more capable

//joinExpression
//    : joinDereferencedExpression EQ^ joinDereferencedExpression
//	;
joinExpression
    : joinDereferencedExpression EQ joinDereferencedExpression
	;

//joinDereferencedExpression
//	:	 (namespaced_name -> ^(FIELDREF namespaced_name))
//	;
joinDereferencedExpression
	:	 namespaced_name
	;

// --------- expressions ------------

//expression[boolean select]
//scope {
//    boolean in_select;
//}
//@init {
//    $expression::in_select = $select;
//}
//	: ( annotation logicalORExpression) -> ^(ANNOTATE annotation logicalORExpression)
//	| logicalORExpression
//	;

expression [boolean select] 
@init {
	expression_stack.push(new expression_scope());
    expression_stack.peek().in_select = select;
}
@after {
	expression_stack.pop();	
}
	: annotateExpression
	| logicalORExpression
	| nullOperator
	;
	
nullOperator
	: 'null'
	;

//annotation
//    : LBRACKET! constantMapExpression RBRACKET!
//    ;
annotateExpression
	: annotation logicalORExpression
	;
annotation
    : LBRACKET constantMapExpression RBRACKET
    ;

//logicalORExpression
//	: logicalANDExpression (OR logicalANDExpression)+ -> ^(OR logicalANDExpression+)
//	| logicalANDExpression
//	;
logicalORExpression
	: logicalANDExpression (OR logicalANDExpression)+
	| logicalANDExpression
	;
		
//logicalANDExpression
//	: equalityExpression (AND equalityExpression)+ -> ^(AND equalityExpression+)
//	| equalityExpression
//	;
logicalANDExpression
	: equalityExpression (AND equalityExpression)*
	;

//equalityExpression
//	: relationalExpression (  ((IN^ | NOT_IN^) inNotInTarget)
//	                         | (IS_NULL^ | IS_NOT_NULL^)
//	                         | (equalityOp^ relationalExpression) )?
//	;

equalityExpression //changed for parsing literal tests
	: relationalExpression (  ((IN | NOT_IN) inNotInTarget)
	                         | (IS_NULL | IS_NOT_NULL)
	                         | (equalityOp relationalExpression) )
	 | relationalExpression
	;

//inNotInTarget
//    : {$expression::in_select}? LPAREN select_statement RPAREN -> ^(QUERY_ARRAY select_statement)
//    | literal_list
//    ;
inNotInTarget
    : {expression_stack.peek().in_select}? LPAREN select_statement RPAREN
    | literal_list
    ;

equalityOp 
	:	(EQ | NEQ | LIKE | NOTLIKE | MATCHES | NOTMATCHES | CONTAINS)
	;
	
//relationalExpression
//	: additiveExpression (relationalOp^ additiveExpression)*
//	;
relationalExpression
	: additiveExpression (relationalOp additiveExpression)?
	;

relationalOp
	:	(LT | GT | LTEQ | GTEQ)
	;
	
//additiveExpression
//	: multiplicativeExpression (additiveOp^ additiveExpression)?
//	;
additiveExpression
	: multiplicativeExpression (additiveOp additiveExpression)?
	;
	
//additiveOp
//	:	'+' -> BINOP_ADD
//	|   '-' -> BINOP_SUB
//	;
additiveOp
	:	'+'
	|   '-'
	;	
	
//multiplicativeExpression
//	: unaryExpression (multOp^ multiplicativeExpression)?
//	;
multiplicativeExpression
	: unaryExpression (multOp multiplicativeExpression)?
	;
	
//multOp	:	'*' -> BINOP_MULT
//	|       '/' -> BINOP_DIV
//	|       '%' -> BINOP_MOD
//	;
multOp	
	:	'*'
	|   '/'
	|   '%'
	;

//unaryOp
//    : '-'       -> UNOP_MINUS
//    | '!'       -> UNOP_NOT
//	;	
unaryOp
    : '-'
    | '!'
	;	
	
//unaryExpression
//    : dereferencedExpression
//    | unaryOp^ dereferencedExpression
//	;
unaryExpression
    : dereferencedExpression
    | unaryOp dereferencedExpression
	;
	
//dereferencedExpression
//	:	 (primaryExpression -> primaryExpression)
//	     (
//	        (LBRACKET idx=expression[$expression::in_select] RBRACKET) -> ^(INDEXREF $dereferencedExpression $idx)
//          | (DOT nm=ID)                        -> ^(PROPERTYREF $dereferencedExpression $nm)
//	     )*
//	;
dereferencedExpression
@init{
	boolean	in_select = expression_stack.peek().in_select;	
}
	:	 primaryExpression
	     (
	        indexref[in_select]
          | propertyref
	     )*
	;
	
indexref[boolean in_select]
	:	LBRACKET idx=expression[in_select] RBRACKET
	;
propertyref
	: 	DOT nm=ID
	;
//operatorCall
//    : multOp arguments[$expression::in_select]      -> ^(multOp arguments)
//    | additiveOp arguments[$expression::in_select]  -> ^(additiveOp arguments)
//    | AND arguments[$expression::in_select]         -> ^(AND arguments)
//    | OR arguments[$expression::in_select]          -> ^(OR arguments)
//    ;
operatorCall
@init{
	boolean	in_select = expression_stack.peek().in_select;	
}
    : multOp arguments[in_select]
    | additiveOp arguments[in_select]
    | AND arguments[in_select]
    | OR arguments[in_select]
    ;


// TODO: temporarily disable CAST, need to think through how types are named

//primaryExpression
//	: namespaced_name arguments[$expression::in_select]                 -> ^(CALL namespaced_name arguments)
////	| CAST LPAREN expression[$expression::in_select] AS typename RPAREN -> ^(CAST expression typename)
//	| APPLY! operatorCall
//	| parameter
//	| namespaced_name                               -> ^(FIELDREF namespaced_name)
//	| scalar_literal
//	| LBRACKET i+=expression[$expression::in_select]? (COMMA (i+=expression[$expression::in_select]))* RBRACKET        -> ^(ARRAY_LITERAL $i*)
//	| mapExpression
//	| LPAREN! expression[$expression::in_select] RPAREN!
//	;
primaryExpression
@init {
    boolean in_select = expression_stack.peek().in_select;
}
	: callExpresion[in_select]
//	| CAST LPAREN expression[$expression::in_select] AS typename RPAREN -> ^(CAST expression typename)
//	| APPLY operatorCall
	| parameter
	| fieldref
	| scalar_literal
	| arrayLiteral
	| mapExpression
	| LPAREN expression[in_select] RPAREN
	;
	
callExpresion[boolean in_select]
	: namespaced_name arguments[in_select]
	;
	
fieldref
	: namespaced_name
	;
arrayLiteral
@init {
	boolean in_select = expression_stack.peek().in_select;
}
    : LBRACKET expression[in_select]? (COMMA expression[in_select])* RBRACKET
	;

// a parameter is an argument from outside the script/program
//parameter 
//	:	AT ident -> ^(PARAMETER ident)
//	;
parameter 
	: AT ident
	;	
	       
//propertyNameAndValue
//	: propertyName ':' expression[$expression::in_select] -> ^(PROPERTY propertyName expression)
//	;
propertyNameAndValue
	: propertyName ':' expression[{expression_stack.peek().in_select}] //{return (PROPERTY propertyName expression);}
	;

//constantPropertyNameAndValue
//	: propertyName ':' constantExpression  -> ^(PROPERTY propertyName constantExpression)
//	;
constantPropertyNameAndValue
	: propertyName ':' constantExpression
	;

//propertyName
//	: ID     -> STRING<LiteralNode>[$ID, $ID.text]
//	| literalString
//	;
propertyName
	: ID 
	| literalString
	;

constantExpression
    : scalar_literal
    | constantMapExpression
    | constantArray
    ;

//constantArray
//    : LBRACKET i+=constantExpression? (COMMA i+=constantExpression)* RBRACKET -> ^(ARRAY_LITERAL $i*)
//    ;
constantArray
    : LBRACKET i+=constantExpression? (COMMA i+=constantExpression)* RBRACKET
    ;

// TODO: fix INT L and INT b parsing -- should not permit whitespace between them
//scalar_literal
//	: TRUE    -> TRUE<LiteralNode>[$TRUE, true]
//	| FALSE   -> FALSE<LiteralNode>[$FALSE, false]
//	| literalString
//	| LONG_INT -> INT<LiteralNode>[$LONG_INT, Long.parseLong($LONG_INT.text.substring(0, $LONG_INT.text.length()-1))]
////	| INT 'b' -> INT<LiteralNode>[$INT, Byte.parseByte($INT.text)]
//	| INT     -> INT<LiteralNode>[$INT, Integer.parseInt($INT.text)]
//	| FLOAT   -> FLOAT<LiteralNode>[$FLOAT, Double.parseDouble($FLOAT.text)]
//	;
scalar_literal
	: TRUE    
	| FALSE 
	| STRING
	| LONG_INT
//	| INT 'b' -> INT<LiteralNode>[$INT, Byte.parseByte($INT.text)]
	| INT     
	| FLOAT
	;
	
//literalString
//    : STRING -> STRING<LiteralNode>[$STRING, StringUnescaper.unquote($STRING.text)]
//    ;
literalString
	: STRING
	;

//array_parameter
//    : AT i=ident {isArrayParameter($i.text)}? -> ^(PARAMETER $i)
//    ;
array_parameter
    : AT i=ident {isArrayParameter($i.ctx)}?
    ;
    
// array literal for IN/NOT_IN using SQL syntax
//literal_list
//    : LPAREN array_parameter RPAREN -> array_parameter
//	| LPAREN literal_element (COMMA literal_element)* RPAREN -> ^(ARRAY_LITERAL literal_element+)
//	;
literal_list
	: LPAREN literal_element (COMMA literal_element)* RPAREN //{return ^(ARRAY_LITERAL literal_element+);}
	;
	
literal_element
    : scalar_literal
    | parameter
    ;

//fixed_or_parameter
//    : INT    -> INT<LiteralNode>[$INT, Integer.parseInt($INT.text)]
//	| parameter
//	;
fixed_or_parameter
    : INT
	| parameter
	;


// INSERT

//insert_statement
//    : INSERT^ insert_source insert_values returning_spec?
//    ;
insert_statement
    : INSERT insert_source insert_values returning_spec?
    ;

//insert_source
//    : INTO^ write_data_source
//    ;
insert_source
    : INTO write_data_source
    ;

//write_data_source
//    :	namespaced_name -> ^(CALL_SOURCE namespaced_name)
//    ;
write_data_source
    :	namespaced_name
    ;

//insert_values
//    : field_names_spec VALUES field_values_group_spec (COMMA field_values_group_spec)* -> ^(INSERT_VALUES field_names_spec field_values_group_spec+)
//    | query_statement -> ^(INSERT_QUERY query_statement)
//    ;
    
insert_values
    : field_names_spec VALUES field_values_group_spec (COMMA field_values_group_spec)*
    | query_statement
    ;

//field_names_spec
//    : LPAREN field_def (COMMA field_def)* RPAREN -> field_def+
//    ;
field_names_spec
    : LPAREN field_def (COMMA field_def)* RPAREN
    ;

//field_values_spec
//    : LPAREN expression[true] (COMMA expression[true])* RPAREN -> expression+
//    ;
field_values_spec
    : LPAREN expression[true] (COMMA expression[true])* RPAREN
    ;

//field_values_group_spec
//    : LPAREN expression[true] (COMMA expression[true])* RPAREN -> ^(FIELD_VALUES_GROUP expression+)
//    ;
field_values_group_spec
    : LPAREN expression[true] (COMMA expression[true])* RPAREN
    ;
    
returning_spec
    : RETURNING select_field_spec
    ;

// DELETE

//delete_statement
//    : DELETE^ delete_source where? returning_spec?
//    ;
delete_statement
    : DELETE delete_source where? returning_spec?
    ;

//delete_source
//    : FROM^ write_data_source
//    ;
delete_source
    : FROM write_data_source
    ;

// UPDATE

//update_statement
//    : UPDATE^ update_source SET update_values where? returning_spec?
//    ;
update_statement
    : UPDATE update_source SET update_values where? returning_spec?
    ;

//update_source
//    : namespaced_name
//    ;
update_source
    : write_data_source
    ;

//update_values
//    : field_names_spec EQ field_values_spec -> ^(UPDATE_VALUES field_names_spec field_values_spec)
//    | field_def (COMMA field_def)* -> ^(UPDATE_VALUES field_def+)
//    ;
update_values
    : field_names_spec EQ field_values_spec
    | field_def (COMMA field_def)*
    ;

   


