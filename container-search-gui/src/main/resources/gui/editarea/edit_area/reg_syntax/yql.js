/**
* Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
*/
function getSources(bool){
		var options = [];
		if (window.CONFIG.hasOwnProperty("model_sources")){
			var sources = window.CONFIG.model_sources;
			for (i in sources){
				option = [sources[i]];
				options.push(option);
			}
			if (bool == true){options.push(["*"])}
		}
		return options;
}

editAreaLoader.load_syntax["yql"] = {
	'DISPLAY_NAME' : 'YQL'
	,'QUOTEMARKS' : {1: "'", 2: '"', 3: '`'}
	,'KEYWORD_CASE_SENSITIVE' : false
	,'OPERATOR_CASE_SENSITIVE' : false
	,'KEYWORDS' : {
		'statements' : [
			'SELECT', 'FROM','SOURCES', 'CONTAINS',
		 	'NOT', 'ORDER',
			'BY', 'WHERE'
		]
		,'reserved' : [
			'null'

		]
		,'functions' : [
   'DESC', 'ASC', 'ALL', 'GROUP', 'RANGE', 'EACH', 'OUTPUT', 'SUM', 'LIMIT', 'OFFSET', 'TIMEOUT'
		]
	}
	,'OPERATORS' :[
     'COUNT','AND','and','OR','or','BETWEEN','between','&&','&','|','^','/','<=>','=','>=','>','<<','>>','<=','<','-','%','!=','<>','!','||','+','~','*'
	]
	,'DELIMITERS' :[
		'(', ')', '[', ']', '{', '}'
	]
	,'REGEXPS' : {
		// highlight all variables (@...)
		'variables' : {
			'search' : '()(\\@\\w+)()'
			,'class' : 'variables'
			,'modifiers' : 'g'
			,'execute' : 'before' // before or after
		}
	}
	,'STYLES' : {
		'COMMENTS': 'color: #AAAAAA;'
		,'QUOTESMARKS': 'color: #879EFA;'
		,'KEYWORDS' : {
			'reserved' : 'color: #48BDDF;'
			,'functions' : 'color: #0040FD;'
			,'statements' : 'color: #60CA00;'
			}
		,'OPERATORS' : 'color: #FF00FF;'
		,'DELIMITERS' : 'color: #d1421b;'
		,'REGEXPS' : {
			'variables' : 'color: #E0BD54;'
		}
	},
	'AUTO_COMPLETION' :  {
		"default": {	// the name of this definition group. It's posisble to have different rules inside the same definition file
			"REGEXP": { "before_word": "[^a-zA-Z0-9_]|^"	// \\s|\\.|
						,"possible_words_letters": "[a-zA-Z0-9_]+"
						,"letter_after_word_must_match": "[^a-zA-Z0-9_]|$"
						,"prefix_separator": "\\."
					}
			,"CASE_SENSITIVE": false
			,"MAX_TEXT_LENGTH": 50		// the maximum length of the text being analyzed before the cursor position
			,"KEYWORDS": {
				'': [	// the prefix of these items
						/**
						 * 0 : the keyword the user is typing
						 * 1 : (optionnal) the string inserted in code ("{@}" being the new position of the cursor, "§" beeing the equivalent to the value the typed string indicated if the previous )
						 * 		If empty the keyword will be displayed
						 * 2 : (optionnal) the text that appear in the suggestion box (if empty, the string to insert will be displayed)
						 */
						 ['SELECT','SELECT'],
						 ['FROM','FROM'],
 						 ['SOURCES','SOURCES'],
						 ['CONTAINS','CONTAINS'],
						 ['NOT','NOT'], ['ORDER','ORDER'], ['BY','BY'],['WHERE','WHERE'],
						 ['DESC','DESC'],['ASC','ASC'],['ALL','ALL'], ['GROUP','GROUP'],
						 ['RANGE','RANGE'],['EACH','EACH'],['OUTPUT','OUTPUT'],
						 ['SUM','SUM'],['LIMIT','LIMIT'],['OFFSET','OFFSET'],
						 ['TIMEOUT','TIMEOUT']
					 ]
			}
		},
		"sources": {	// the name of this definition group. It's posisble to have different rules inside the same definition file
			"REGEXP": { "before_word": "[^a-zA-Z0-9_]|^"	// \\s|\\.|
						,"possible_words_letters": "[a-zA-Z0-9_]+"
						,"letter_after_word_must_match": "[^a-zA-Z0-9_]|$"
						,"prefix_separator": " "
					}
			,"CASE_SENSITIVE": false
			,"MAX_TEXT_LENGTH": 50		// the maximum length of the text being analyzed before the cursor position
			,"KEYWORDS": {
					'SOURCES' : getSources(true) ,
					',' : getSources(false)
			}
		}

	}
};
