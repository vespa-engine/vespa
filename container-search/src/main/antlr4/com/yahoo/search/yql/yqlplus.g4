// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
grammar yqlplus;

options {
  superClass = ParserBase;
  language = Java;
}

@header {
  import java.util.Deque;
  import java.util.ArrayDeque;
  import com.yahoo.search.yql.*;
}

@parser::members {
    protected Deque<Boolean> expression_stack = new ArrayDeque<>();
}

// tokens

  SELECT : 'select';

  LIMIT : 'limit';
  OFFSET : 'offset';
  WHERE : 'where';
  ORDERBY : 'order by';
  DESC : 'desc';
  FROM : 'from';
  SOURCES : 'sources';
  AS : 'as';

  COMMA : ',';
  OUTPUT : 'output';
  COUNT : 'count';

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

  TIMEOUT : 'timeout';


/*------------------------------------------------------------------
 * LEXER RULES
 *------------------------------------------------------------------*/

IDENTIFIER  :	('a'..'z'|'A'..'Z'|'_') ('a'..'z'|'A'..'Z'|'0'..'9'|'_'|'-')*
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

STRING  :  DQ ( ESC_SEQ | ~('\\'| '"') )* DQ
        |  SQ ( ESC_SEQ | ~('\\' | '\'') )* SQ
	;

fragment
HEX_DIGIT : ('0'..'9'|'a'..'f'|'A'..'F') ;
	

fragment
ESC_SEQ
    :   '\\' ('b'|'t'|'n'|'f'|'r'|'"'|'\''|'\\'|'/')
    |   UNICODE_ESC
    ;

fragment
UNICODE_ESC
    :   '\\' 'u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT
    ;

WS  :   ( ' '
        | '\t'
        | '\r'
        | '\n'
  	) -> channel(HIDDEN)
    ;

COMMENT
    :  ( ('//') ~('\n'|'\r')* '\r'? '\n'? 
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

// --------- parser rules ------------

ident
   : keyword_as_ident
   | IDENTIFIER
   ;

keyword_as_ident
   : SELECT | LIMIT | OFFSET | WHERE | 'order' | 'by' | DESC | OUTPUT | COUNT | SOURCES | MATCHES | LIKE
   ;

program : (statement SEMI?)* EOF
	;

statement
      : output_statement
	  ;

output_statement
      : source_statement output_spec?
      ;

source_statement
      : query_statement (PIPE pipeline_step)*
      ;

pipeline_step
      : namespaced_name arguments[false]?
      | vespa_grouping
      ;

vespa_grouping
      : VESPA_GROUPING
      | annotation VESPA_GROUPING
      ;

output_spec
      : (OUTPUT AS ident)
      | (OUTPUT COUNT AS ident)
      ;

query_statement
    : select_statement
    ;

select_statement
	:	SELECT select_field_spec select_source? where? orderby? limit? offset? timeout?
    ;

select_field_spec
	:	project_spec
	| 	STAR
	;
	
project_spec
	: field_def (COMMA  field_def)*
	;

timeout
	:  TIMEOUT fixed_or_parameter
	;

select_source
    :   select_source_all
    |   select_source_multi
	|	select_source_from
	;

select_source_all
	:   FROM SOURCES STAR
	;

select_source_multi
 	: 	FROM SOURCES source_list
 	;
 	
select_source_from
	:	FROM source_spec
	;

source_list
    : namespaced_name (COMMA namespaced_name )*
    ;

source_spec
	:	( data_source (alias_def { ($data_source.ctx).addChild($alias_def.ctx); })? )
	;

alias_def
	:	(AS? IDENTIFIER)
	;

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

namespaced_name
    :   (dotted_idents (DOT STAR)?)
    ;

orderby
	:	ORDERBY orderby_fields
	;
	
orderby_fields
	:	orderby_field (COMMA orderby_field)*
	;	
	
orderby_field
	:   expression[true] DESC
	|	expression[true] ('asc')?
	;	

limit
	:	LIMIT fixed_or_parameter
	;

offset
    : OFFSET fixed_or_parameter
	;

where
	: WHERE expression[true]
	;

field_def
	: expression[true] alias_def?
	;

map_expression
    : LBRACE property_name_and_value? (COMMA property_name_and_value)* RBRACE
    ;

arguments[boolean in_select]
	:  LPAREN RPAREN                                                    
	|  LPAREN (argument[$in_select] (COMMA argument[$in_select])*) RPAREN
	;

argument[boolean in_select]
    : expression[$in_select]
    ;

// --------- expressions ------------

expression [boolean select]
@init {
	expression_stack.push(select);
}
@after {
	expression_stack.pop();	
}
	: annotate_expression
	| logical_OR_expression
	| null_operator
	;
	
null_operator
	: 'null'
	;

annotate_expression
	: annotation logical_OR_expression
	;

annotation
    : LBRACKET map_expression RBRACKET
    | map_expression
    ;

logical_OR_expression
	: logical_AND_expression (OR logical_AND_expression)+
	| logical_AND_expression
	;
		
logical_AND_expression
	: equality_expression (AND equality_expression)*
	;

equality_expression
	: relational_expression (  ((IN | NOT_IN) in_not_in_target)
	                         | (IS_NULL | IS_NOT_NULL)
	                         | (equality_op relational_expression) )
	 | relational_expression
	;

in_not_in_target
    : {expression_stack.peek()}? LPAREN select_statement RPAREN
    | literal_list
    ;

equality_op
	:	(EQ | NEQ | LIKE | NOTLIKE | MATCHES | NOTMATCHES | CONTAINS)
	;
	
relational_expression
	: additive_expression (relational_op additive_expression)?
	;

relational_op
	:	(LT | GT | LTEQ | GTEQ)
	;
	
additive_expression
	: multiplicative_expression (additive_op additive_expression)?
	;
	
additive_op
	:	'+'
	|   '-'
	;	
	
multiplicative_expression
	: unary_expression (mult_op multiplicative_expression)?
	;
	
mult_op
	:	'*'
	|   '/'
	|   '%'
	;

unary_op
    : '-'
    | '!'
	;	
	
unary_expression
    : dereferenced_expression
    | unary_op dereferenced_expression
	;
	
dereferenced_expression
@init{
	boolean	in_select = expression_stack.peek();
}
	:	 primary_expression
	     (
	        indexref[in_select]
          | propertyref
	     )*
	;
	
indexref[boolean in_select]
	:	LBRACKET idx=expression[in_select] RBRACKET
	;
propertyref
	: 	DOT nm=IDENTIFIER
	;

primary_expression
@init {
    boolean in_select = expression_stack.peek();
}
	: call_expression[in_select]
	| fieldref
	| constant_expression
	| LPAREN expression[in_select] RPAREN
	;
	
call_expression[boolean in_select]
	: namespaced_name arguments[in_select]
	;

fieldref
	: namespaced_name
	;

// a parameter is an argument from outside the YQL statement
parameter
	: AT ident
	;	
	       
property_name_and_value
	: property_name ':' constant_expression
	;

property_name
	: dotted_idents
	| STRING
	;

dotted_idents
    :   ident (DOT ident)*
    ;

constant_expression
    : scalar_literal
    | map_expression
    | array_literal
    | parameter
    ;

array_literal
    : LBRACKET i+=constant_expression? (COMMA i+=constant_expression)* RBRACKET
    ;

scalar_literal
	: TRUE    
	| FALSE 
	| STRING
	| LONG_INT
	| INT
	| FLOAT
	;
	
array_parameter
    : AT i=ident {isArrayParameter($i.ctx)}?
    ;
    
literal_list
	: LPAREN literal_element (COMMA literal_element)* RPAREN
	;
	
literal_element
    : scalar_literal
    | parameter
    ;

fixed_or_parameter
    : INT
	| parameter
	;
