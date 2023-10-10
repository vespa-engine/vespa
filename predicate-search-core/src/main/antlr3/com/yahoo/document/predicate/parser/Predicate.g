// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
grammar Predicate;

@header {
package com.yahoo.document.predicate.parser;

import com.yahoo.document.predicate.Conjunction;
import com.yahoo.document.predicate.Disjunction;
import com.yahoo.document.predicate.FeatureRange;
import com.yahoo.document.predicate.FeatureSet;
import com.yahoo.document.predicate.Negation;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.document.predicate.Predicates;
}

@lexer::header {
package com.yahoo.document.predicate.parser;

import com.yahoo.document.predicate.Predicate;
}

@members {
    @Override
    public void emitErrorMessage(String message) {
        throw new IllegalArgumentException(message);
    }
}

@lexer::members {
    @Override
    public void emitErrorMessage(String message) {
        throw new IllegalArgumentException(message);
    }
}

predicate returns [Predicate n]
    : d=disjunction EOF { n = d; }
    ;

disjunction returns [Predicate n]
    : c=conjunction { n = c; }
      ( OR c2=conjunction { n = new Disjunction(n, c2); }
        ( OR c3=conjunction { ((Disjunction)n).addOperand(c3); } )* )?
    ;

conjunction returns [Predicate n]
    : u=unary_node { n = u; }
      ( AND u2=unary_node { n = new Conjunction(n, u2); }
        ( AND u3=unary_node { ((Conjunction)n).addOperand(u3); } )* )?
    ;

unary_node returns [Predicate n]
    : l=leaf { n = l; }
    | ( not=NOT )? '(' d=disjunction ')' { n = d; if (not != null) { n = new Negation(n); } }
    ;

leaf returns [Predicate n]
    : k=value ( not=NOT )? IN
        ( mv=multivalue[k] { n = mv; }
        | r=range[k] { n = r; }
        ) { if (not != null) { n = new Negation(n); } }
    | TRUE { n = Predicates.value(true); }
    | FALSE { n = Predicates.value(false); }
    ;

multivalue[String key] returns [FeatureSet n]
    : '[' v1=value { n = new FeatureSet(key, v1); }
          ( ',' v2=value { n.addValue(v2); })* ']'
    ;

value returns [String s]
    : VALUE { s = $VALUE.text; }
    | STRING { s = $STRING.text; }
    | INTEGER { s = $INTEGER.text; }
    | k=keyword { s = k; }
    ;

range[String key] returns [FeatureRange r]
    : '[' { r = new FeatureRange(key); }
          ( i1=INTEGER { r.setFromInclusive(Long.parseLong(i1.getText())); } )?
          '..'
          ( i2=INTEGER { r.setToInclusive(Long.parseLong(i2.getText())); } )?
          ( '(' partition (',' partition)* ')' )?
      ']'
    ;

partition
    : value ('='|'=-') (INTEGER INTEGER /* second integer becomes negative */ | INTEGER '+[' INTEGER? '..' INTEGER? ']')
    ;

keyword returns [String s]
    : OR  { s = $OR.text; }
    | AND { s = $AND.text; }
    | NOT { s = $NOT.text; }
    | IN  { s = $IN.text; }
    | TRUE { s = $TRUE.text; }
    | FALSE { s = $FALSE.text; }
    ;

INTEGER : ( '-' | '+' )? ('1'..'9' ( '0'..'9' )* | '0') ;

OR  : 'OR' | 'or' ;
AND : 'AND' | 'and' ;
NOT : 'NOT' | 'not' ;
IN  : 'IN' | 'in' ;
TRUE : 'TRUE' | 'true' ;
FALSE : 'FALSE' | 'false' ;

VALUE : ( 'a'..'z' | 'A'..'Z' | '0'..'9' | '_' )+ ;

STRING
    : ( '\'' ( ~('\'') | '\\\'' )* '\''
      | '\"' ( ~('\"') | '\\\"' )* '\"' )
      { setText(Predicate.asciiDecode(getText().substring(1, getText().length() - 1))); }
    ;

WS  : (' ' | '\t')+
      {$channel = HIDDEN;}
    ;
