;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; form.l -- screen forms handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare
  (specials t)
  (macros t))

(eval-when (compile)
  (load 'utilities)
  (load 'constants)
  (load 'zone)
  (load 'look)
  (load 'font)
  (load 'text)
  (load 'text-edit))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;						generic fields
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defstruct
  (field		; generic field
    (:displace t)
    (:list)
    (:conc-name))
  (type 'generic-field)		; type = generic
  (zone (make-zone))		; bounding zone
  (properties (list nil))	; empty property list
)

(defvar field-properties	; list of expected field properties
  '("field-properties"
    fill-ground		(solid pattern)		; should we draw when highlit?
    fill-colour		(x_colour x_pattern)	; what colour or pattern?
    empty-ground 	(solid pattern)		; should we draw when unlit?
    empty-colour	(x_colour x_pattern)	; what colour or pattern?
    border-colour	(x_colour) ; should we draw border (and what colour?)
   ))	; can use this as real plist for online documentation

(defun draw-field (f)		; draw field from scratch
  (apply (concat 'draw- (field-type f))	; construct draw function name
	 (ncons f)))				; then call it

(defun init-field (f)		; initialize a field
  (apply (concat 'init- (field-type f))	; construct init function name
	 (ncons f)))				; then call it

(defun resize-field (f box)		; resize a field
  (apply				; construct resize function name
    (concat 'resize- (field-type f))
    (list f box)))				; then call it

(defun toggle-field (f)		; toggle a field
  (apply (concat 'toggle- (field-type f)) ; construct toggle fcn name
	 (ncons f)))				; then call it

(defun check-field (f p)	; check if point is inside field excl.border
  (cond ((point-in-box-interior p (zone-box (field-zone f)))
	 (apply			; if so, construct check function name
	   (concat 'check- (field-type f))
	   (list f p)))		; then call it and return result
	(t nil)))		; otherwise return nil

(defun fill-field (f)		; fill the field interior, if defined
  (let ((b (get (field-properties f) 'fill-ground))	; check if has one
	(c (get (field-properties f) 'fill-colour)))
       (cond ((eq b 'solid)	; solid background
	      (cond (c (clear-zone-interior (field-zone f) c))
		    (t (clear-zone-interior (field-zone f) W-CONTRAST))))
	     ((eq b 'pattern)	; patterned background
	      (cond (c (pattern-zone-interior (field-zone f) c))
		    (t (pattern-zone-interior (field-zone f) W-PATTERN-1))))
       )))			; no background at all!

(defun empty-field (f)		; empty the field interior, if defined
  (let ((b (get (field-properties f) 'empty-ground)) ; check if has one
	(c (get (field-properties f) 'empty-colour)))
       (cond ((eq b 'solid)	; solid background
	      (cond (c (clear-zone-interior (field-zone f) c))
		    (t (clear-zone-interior (field-zone f) W-BACKGROUND))))
	     ((eq b 'pattern)	; patterned background
	      (cond (c (pattern-zone-interior (field-zone f) c))
		    (t (pattern-zone-interior (field-zone f) W-PATTERN-1))))
       )))			; no background at all!

(defun draw-field-background (f)	; just what it says
  (let ((b (get (field-properties f) 'empty-ground)) ; check if has one
	(c (get (field-properties f) 'empty-colour)))
       (cond ((eq b 'solid)	; solid background
	      (cond (c (clear-zone (field-zone f) c))
		    (t (clear-zone (field-zone f) W-BACKGROUND))))
	     ((eq b 'pattern)	; patterned background
	      (cond (c (pattern-zone (field-zone f) c))
		    (t (pattern-zone (field-zone f) W-PATTERN-1))))
       )))			; no background at all!

(defun draw-field-border (f)		; draw outline, if any
  (let ((c (get (field-properties f) 'border-colour)))
       (cond (c (draw-zone-outline (field-zone f) c)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;						aggregate fields
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defstruct
  (aggregate-field	; aggregate field = form
    (:displace t)
    (:list)
    (:conc-name))
  (type 'aggregate-field)		; type
  (zone (make-zone))		; bounding zone
  (properties (list nil))	; empty property list
  subfields			; list of subfields
  selection			; which subfield was last hit
)
  
(defvar aggregate-field-properties
  `("aggregate-field-properties"
    = ,field-properties
   ))	; can use this as real plist for online documentation

(defun draw-aggregate-field (f)
  (draw-field-background f)			; clear background, if any
  (draw-field-border f)				; draw border, if any
  (mapc 'draw-field (aggregate-field-subfields f)) ; draw subfields
  (w-flush (window-w (zone-window (field-zone f)))) t) ; flush it out

(defun init-aggregate-field (f)
  (mapc 'init-field (aggregate-field-subfields f))
  (alter-aggregate-field f selection nil) t)

(defun resize-aggregate-field (f box)
  (alter-zone (field-zone f) box box))

(defun check-aggregate-field (f p)
  (do ((subfields (aggregate-field-subfields f)	; go through subfields
	 (cdr subfields))
       (gotcha))
      ((or (null subfields)				; stop when no more
	   (setq gotcha (check-field (car subfields) p))) ; or when one is hit
       (alter-aggregate-field f selection gotcha)	; remember which one
       gotcha)))					; also return it

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;						remote fields
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; A remote field is a field which activates another field when hit.
;;; Usually the remote field has some functional significance!

(defstruct
  (remote-field		; remote field
    (:displace t)
    (:list)
    (:conc-name))
  (type 'remote-field)		; type = remote
  (zone (make-zone))		; bounding zone
  (properties (list nil))	; empty plist
  (target)			; the actual target field
  (point)			; x,y coords to pretend to use
)

(defvar remote-field-properties
  `("remote-field-properties"
    = ,field-properties
   ))	; can use this as real plist for online documentation

(defun draw-remote-field (f) 't)	; nothing to draw

(defun init-remote-field (f) 't)	; nothing to initialize

(defun resize-remote-field (f box)
  (alter-zone (field-zone f) box box))

(defun check-remote-field (f p)
  (check-field
    (remote-field-target f)
    (remote-field-point f)))		; return result of checking target

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;						button fields
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defstruct
  (button-field		; button field
    (:displace t)
    (:list)
    (:conc-name))
  (type 'button-field)		; type = button
  (zone (make-zone))		; bounding zone
  (properties
    (list nil			; default properties
	  'fill-ground 'solid
	  'empty-ground 'solid
	  'border-colour W-CONTRAST
    ))
  (value nil)			; value
)

(defvar button-field-properties
  `("button-field-properties"
    = ,field-properties
   ))	; can use this as real plist for online documentation

(defun draw-button-field (f)
  (draw-field-border f)
  (cond ((button-field-value f)
	 (fill-field f))
	(t (empty-field f))))

(defun toggle-button-field (f)
  (alter-button-field f value (not (button-field-value f)))
  (clear-zone-interior (field-zone f) W-XOR))

(defun init-button-field (f)
  (alter-button-field f value nil))	; turn it off

(defun resize-button-field (f box)
  (alter-zone (field-zone f) box box))

(defun check-button-field (f p)
  (toggle-button-field f) f)	; if we get here it's a hit -> return self

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;						radio-button fields
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Named for the buttons on radios in which only one is "in" at a time.

(defstruct
  (radio-button-field		; radio-button field
    (:displace t)
    (:list)
    (:conc-name))
  (type 'radio-button-field)		; type = radio-button
  (zone (make-zone))		; bounding zone
  (properties (list nil))	; empty plist
  (subfields nil)		; individual buttons
  (selection nil)		; which one last hit
)

(defvar radio-button-field-properties
  `("radio-button-field-properties"
    = ,aggregate-field-properties
   ))	; can use this as real plist for online documentation

(defun draw-radio-button-field (f)
  (draw-aggregate-field f))

(defun init-radio-button-field (f)
  (init-aggregate-field f))

(defun resize-radio-button-field (f box)
  (alter-zone (field-zone f) box box))

(defun check-radio-button-field (f p)
  (cond ((and (radio-button-field-selection f)	; if button previously sel'd
	      (button-field-value
		(radio-button-field-selection f))) ; and it has a value
	 (toggle-field				; turn it off
	   (radio-button-field-selection f))))
  (check-aggregate-field f p)			; check individual buttons
)		; this will turn back on if same one sel'd, and return it

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;						text fields
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defstruct
  (text-field		; text field
    (:displace t)
    (:list)
    (:conc-name))
  (type 'text-field)		; type = text
  (zone (make-zone))		; bounding zone
  (properties
    (list nil
	  'fill-ground 'solid
	  'empty-ground 'solid
	  'border-colour W-CONTRAST
	  'x-offset 5		; offset from left
    ))
  (value nil)
  (text '||)			; text of text
)

(defvar text-field-properties
  `("text-field-properties"
    x-offset (x_pixels)		; text offset from box ll, otherwise centred
    y-offset (x_pixels)		; text offset from box ll, otherwise centred
    + ,button-field-properties
   ))	; can use this as real plist for online documentation

(defun draw-text-field (f)
  (draw-button-field f)
  (w-flush (window-w (zone-window (field-zone f)))) ; guarantee text on top
  (draw-text (text-field-text f)))

(defun redraw-text-field (f)
  (empty-field f)
  (w-flush (window-w (zone-window (field-zone f)))) ; guarantee text on top
  (draw-text (text-field-text f)))

(defun init-text-field (f)	; position & position the text in the field
  (let ((s (text-field-text f))
	(x-offset (get (field-properties f) 'x-offset))	; x offset from ll
	(y-offset (get (field-properties f) 'y-offset))); y offset from ll
       (alter-text s
	 zone (make-zone			; ensure it has a zone
		window (zone-window (field-zone f))
		box (box-interior (zone-box (field-zone f)))))
       (format-text s)		; ensure text delta calculated
       (cond ((null x-offset)		; x-offset specified?
	      (setq x-offset		; nope! centre it left-right
		    (/ (- (x (box-size (zone-box (field-zone f))))
			  (x (text-delta s)))
		       2))))
       (cond ((null y-offset)		; y-offset specified?
	      (setq y-offset		; nope! centre it up-down
		    (/ (- (y (box-size (zone-box (field-zone f))))
			  (font-x-height (look-font (text-look s))))
		       2))))
       (alter-text s			; now position the text
	 offset (make-point x x-offset y y-offset))
       ))

(defun resize-text-field (f box)	; position the text in the field
  (alter-zone (field-zone f) box box)
  (init-text-field f))

(defun check-text-field (f p)
  (input-text-field f) f)	; if we get here it's a hit -> return self

(defun input-text-field (f)
  (alter-text (text-field-text f)
    text '|| nn 0 kr 0 kl 0 delta (make-point x 0 y 0))
  (draw-text-field f)
  (edit-text-field f (ll (zone-box (text-zone (text-field-text f))))))

(defun edit-text-field (f p)		; edit in middle of text field
  (edit-text (text-field-text f) p)	; edit the text
  (draw-field f))			; redraw


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;						prompt fields
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defstruct
  (prompt-field		; prompt field
    (:displace t)
    (:list)
    (:conc-name))
  (type 'prompt-field)		; type = prompt
  (zone (make-zone))		; bounding zone
  (properties
    (list nil 'x-offset 0))	; put it exactly where spec indicates.
  (value nil)
  (text '||)			; text of prompt
)

(defvar prompt-field-properties
  `("prompt-field-properties"
    = ,text-field-properties
   ))	; can use this as real plist for online documentation

(defun draw-prompt-field (f)
  (draw-text-field f))

(defun init-prompt-field (f)
  (init-text-field f))

(defun resize-prompt-field (f box)	; position the text in the field
  (resize-text-field f box))

(defun check-prompt-field (f p) f) ; just return self

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;						text-button fields
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; A text-button is a button tied to a text.
;;; When the button is pressed, the text is input from the keyboard.
;;; Zone could same as either the button (activation by button only)
;;; or include both button & text (should then be adjacent)

(defstruct
  (text-button-field		; text-button field
    (:displace t)
    (:list)
    (:conc-name))
  (type 'text-button-field)		; type = text-button
  (zone (make-zone))		; bounding zone
  (properties (list nil))	; empty plist
  (button)			; button subfield
  (text)			; text subfield
)

(defvar text-button-field-properties
  `("text-button-field-properties"
    = ,field-properties
   ))	; can use this as real plist for online documentation

(defun draw-text-button-field (f)
  (draw-field (text-button-field-button f))
  (draw-text-field (text-button-field-text f)))

(defun init-text-button-field (f)
  (init-field (text-button-field-button f))
  (init-text-field (text-button-field-text f)))

(defun resize-text-button-field (f box)
  (alter-zone (field-zone f) box box))

(defun toggle-text-button-field (f)	; toggle only the button part
  (cond ((button-field-value		; and only if non-nil
	   (text-button-field-button f))
	 (toggle-button-field (text-button-field-button f)))))

(defun check-text-button-field (f p)
  (cond ((check-field (text-button-field-button f) p)
	 (input-text-field			; input from scratch
	   (text-button-field-text f)))	; get the data
	(t (toggle-button-field			; must be pointing at text
	     (text-button-field-button f))	; toggle only the button part
	   (edit-text-field
	     (text-button-field-text f) p))	; edit the data
  )
  (toggle-button-field			; toggle button back
    (text-button-field-button f))
  (alter-button-field (text-button-field-button f)
    value nil)			; keep aggregate from toggling again
  f)					; return self

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;						labelled button fields
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defstruct
  (labelled-button-field ; labelled button field
    (:displace t)
    (:list)
    (:conc-name))
  (type 'labelled-button-field)	; type = labelled-button
  (zone (make-zone))		; bounding zone
  (properties
    (list nil
	  'fill-ground 'solid
	  'empty-ground 'solid
	  'border-colour W-CONTRAST
    ))
  (value nil)			; value
  (text '||)			; label text
)

(defvar labelled-button-field-properties
  `("labelled-button-field-properties"
    = ,text-field-properties
   ))	; can use this as real plist for online documentation

(defun draw-labelled-button-field (f)
  (draw-text-field f))

(defun init-labelled-button-field (f)
  (init-text-field f))

(defun resize-labelled-button-field (f box)
  (resize-text-field f box))

(defun check-labelled-button-field (f p)
  (toggle-button-field f) f)	; if we get here it's a hit -> return self

(defun toggle-labelled-button-field (f)
  (toggle-button-field f))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;						expanded-bitmap fields
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defstruct
  (expanded-bitmap-field	; expanded-bitmap field
    (:displace t)
    (:list)
    (:conc-name))
  (type 'expanded-bitmap-field)	; type = expanded-bitmap
  (zone (make-zone))		; bounding zone
  (properties (list nil))	; empty plist
  (subfields nil)		; individual bits
  (selection nil)		; which one last hit
  (nrows 1)
  (ncols 1)
)

(defvar expanded-bitmap-field-properties
  `("expanded-bitmap-field-properties"
    = ,aggregate-field-properties
   ))	; can use this as real plist for online documentation

(defun draw-expanded-bitmap-field (f)
  (draw-aggregate-field f))

(defun init-expanded-bitmap-field (f)
  (let ((s (divide-points			; calculate x,y dimensions
	     (box-size (zone-box (field-zone f)))
	     (make-point
	       x (expanded-bitmap-field-ncols f)
	       y (expanded-bitmap-field-nrows f)))))
       (do ((z (field-zone f))
	    (r nil)
	    (x (x (ll (zone-box (field-zone f)))))
	    (y (y (ll (zone-box (field-zone f))))
	       (+ y dy))
	    (dx (x s))
	    (dy (y s))
	    (nc (expanded-bitmap-field-nrows f))
	    (nr (expanded-bitmap-field-nrows f))
	    (j 0 (1+ j)))
	   ((= j nr) (alter-aggregate-field f subfields (nreverse r)) 't)
	   (do ((x x (+ x dx))
		(p)
		(i 0 (1+ i)))
	       ((= i nc))			; create a row of buttons
	       (setq p (make-point x x y y))
	       (setq r (xcons r (make-button-field zone (append z nil))))
	       (alter-zone (field-zone (car r))
		 box (make-box ll p ur (add-points p s)))
	   ))))

(defun resize-expanded-bitmap-field (f box)
  (alter-zone (field-zone f) box box)
  (let ((s (divide-points			; calculate x,y dimensions
	     (box-size box)
	     (make-point
	       x (expanded-bitmap-field-ncols f)
	       y (expanded-bitmap-field-nrows f)))))
       (do ((z (field-zone f))
	    (r (expanded-bitmap-field-subfields f))
	    (x (x (ll box)))
	    (y (y (ll box)) (+ y dy))
	    (dx (x s))
	    (dy (y s))
	    (nc (expanded-bitmap-field-nrows f))
	    (nr (expanded-bitmap-field-nrows f))
	    (j 0 (1+ j)))
	   ((= j nr) t)
	   (do ((x x (+ x dx))
		(p)
		(i 0 (1+ i)))
	       ((= i nc))			; create a row of buttons
	       (setq p (make-point x x y y))
	       (resize-button-field (car r)
		 (make-box ll p ur (add-points p s)))
	       (setq r (cdr r))
	   ))))

(defun check-expanded-bitmap-field (f p)
  (check-aggregate-field f p))	; if we get here it's a hit -> check subfields

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; utilities.l								;
;;;									;
;;; These macros and functions are thought to be generally useful.	;
;;;									;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;							Macros		;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare
  (macros t)		; keep macros around after compiling
  (localf pairify* pairifyq* split2* sublist*)
  (special compiled-with-help))

(defmacro copy-all-but-last (ls)	; copy all but last member of list
  `(let ((ls ,ls))
	(firstn (1- (length ls))
	  ls)))

(defmacro all-but-last (ls)		; destructive all-but-last
  `(let ((ls ,ls))
	(cond ((cdr ls)
	       (rplacd (nthcdr (- (length ls) 2) ls) nil)
	       ls))))

(def hex (macro (arglist)		; hex to integer conversion
		`(car (hex-to-int ',(cdr arglist)))))

;;; define properties on symbols for use by help routines

(defmacro def-usage (fun usage returns group)
  (cond (compiled-with-help	; flag controls help generation
	  `(progn (putprop ,fun ,usage 'fcn-usage)
		  (putprop ,fun ,returns 'fcn-returns)
		  (putprop ,fun (nconc ,group (ncons ,fun)) 'fcn-group)))))
(defvar compiled-with-help t)	; unless otherwise notified

;;; (letenv 'l_bind_plist g_expr1 ... g_exprn) -- pair-list form of "let"
;;; Lambda-binds pairs of "binding-objects" (see description of let,let*),
;;; at RUN TIME, then evaluates g_expr1 to g_exprn, returning g_exprn. eg:
;;; (apply 'letenv '(letenv '(a 1 b (+ c d))
;;;		      (e)(f g)))
;-> (eval (cons 'let (cons (pairify '(a 1 b (+ c d)))
;;;			   '((e) (f g)))))
;-> (let ((a 1) (b (+ c d)))
;;;	 (e) (f g))
(def letenv
  (macro (x)
    `(eval (cons 'let
	     (cons
	       (pairify ,(cadr x))	; plist of binding objects
	       ',(cddr x))))))		; exprs to be eval'ed

(def letenvq			; letenv, quoted binding objects
  (macro (x)
    `(eval (cons 'let
	     (cons
	       (pairifyq ,(cadr x))	; plist of binding objects
	       ',(cddr x))))))		; exprs to be eval'ed

(defmacro mergecar (L1 L2 cmpfn)	; merge, comparing by car's
  `(merge ,L1 ,L2 '(lambda (e1 e2)		; (like sortcar)
		     (funcall ,cmpfn (car e1) (car e2)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;							Functions	;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; (all-but-last l_items)	-- copy all but last list element

;(defun all-but-last (ls)
;  (cond ((cdr ls) (cons (car ls) (all-but-last (cdr ls))))))

;;; (alphap sx_char)
(defun alphap (char)			; is char alphabetic?
  (cond ((symbolp char)
	 (setq char (car (exploden char)))))
  (and (fixp char)
       (or (and (>& char #.(1- #/A))
		(<& char #.(1+ #/Z)))
	   (and (>& char #.(1- #/a))
		(<& char #.(1+ #/z))))))

;;; (alphanumericp sx_char)
(defun alphanumericp (char)		; is char alphabetic or numeric?
  (cond ((symbolp char)
	 (setq char (car (exploden char)))))
  (and (fixp char)
       (or (and (>& char #.(1- #/A))
		(<& char #.(1+ #/Z)))
	   (and (>& char #.(1- #/a))
		(<& char #.(1+ #/z)))
	   (and (>& char #.(1- #/0))
		(<& char #.(1+ #/9))))))

;;; (assqonc 'g_key 'g_val 'l_al)
;;; like   (cond ((assq key alist))
;;;		 (t (cadr (rplacd (last alist)
;;;			    (ncons (cons key val))))))
(defun assqonc (key val al)	; tack (key.val) on end if not found
  (do ((al al (cdr al)))
      ((or (eq key (caar al))
	   (and (null (cdr al))
		(rplacd al (setq al (ncons (cons key val))))))
       (car al))))

;;; (cartesian l_xset l_yset)
(defun cartesian (xset yset)		; cartesian product of elements
  (mapcan
    '(lambda (x)
       (mapcar
	 '(lambda (y) (cons x y))
	 yset))
    xset))

(defun concat-pairs (sb-list)	; concat neighbouring symbol pairs
  (do ((s1 (car sb-list) s2)
       (s2 (cadr sb-list) (car sbs-left))
       (sbs-left (cddr sb-list) (cdr sbs-left))
       (result nil (cons (concat s1 s2) result)))
      ((null s2) (nreverse result))))
;;; (detach l)
;;; Detaches (and throws away) first element of list (converse of attach)
;;; keeping the same initial list cell.
(defun detach (l)
  (cond (l (rplacd l (cddr (rplaca l (cadr l)))))))

;;; (distribute x_Q x_N)
;;; returns list of the form: (1 1 1 0 0 0 0 1 1) or (3 2 2 2 3)
;;; i.e. a list of length <N> containing quantity <Q> evenly distributed
;;; with the excess <Q mod N> surrounding a "core" of <Q div N>'s
;;; Useful (?) for padding spaces in line adjustment.
;(defun distribute (Q N)	; this one only does 1's and 0's
;  (cond ((signp le Q) (duplicate N 0))
;	((eq Q 1) (pad 0 N '(1)))
;	(t (cons 1 (nconc
;		     (distribute (- Q 2) (- N 2))
;		     '(1))))))

(defun distribute (Q N)		; distribute quantity Q among N elements
  (let ((tmp (Divide (abs Q) N)))
       (setq tmp (distribute0 (cadr tmp) N (car tmp) (1+ (car tmp))))
       (cond ((signp ge Q) tmp)
	     (t (mapcar 'minus tmp)))))

(defun distribute0 (Q N X X1)
  (cond ((signp le Q) (duplicate N X))
	((eq Q 1) (pad X N (ncons X1)))
	(t (cons X1 (nconc
			  (distribute0 (- Q 2) (- N 2) X X1)
			  (ncons X1))))))

;;; (duplicate x_n g_object)
;;; Returns list of n copies of object (nil if n <= 0)
(defun duplicate (n object)
  (do ((res nil (cons object res))
       (i n (1- i)))
      ((signp le i) res)))

(defun e0 (in out)		; simulate binary insertion procedure
  (let ((lin (length in))
	(lout (length out)))
       (cond ((> lin lout)
	      (e0
		(nthcdr lout in)
		(mapcan 'list out (firstn lout in))))
	     (t (nconc (mapcan 'list (firstn lin out) in)
		       (nthcdr lin out))))))

(defun e (files)		; determine file permutation for emacs insert
  (let ((i (e0 (cdr (iota (length files))) '(0)))
	(f (append files nil)))
       (mapc '(lambda (f-index f-name)
		(rplaca (nthcdr f-index f) f-name))
	     i files)
       f))

;;; (firstn x_n l_listarg)
(defun firstn (n l)		;  copy first <n> elements of list
  (do ((n n (1- n))
       (l l (cdr l))
       (r nil))
      ((not (plusp n)) (nreverse r))		; <nil> if n=0 or -ve
      (setq r (cons (car l) r))))

;;; (iota x_n)
;;; APL index generator (0,1,2,...,<n>-1)
(defun iota (n)
  (do ((i (1- n) (1- i))
       (res nil))
      ((minusp i) res)
      (setq res (cons i res))))

(defun hex-to-int (numlist)		; eg. (hex-to-int '(12b3 120 8b))
  (cond
    (numlist			; terminate recursion on null numlist
      (cons
	(apply '+
	       (maplist
		 '(lambda (digits)
		    (lsh
		      (get '(hex |0| 0 |1| 1 |2| 2 |3| 3
				 |4| 4 |5| 5 |6| 6 |7| 7
				 |8| 8 |9| 9  a 10  b 11
				  c 12  d 13  e 14  f 15)
			   (car digits))
		      (lsh (1- (length digits)) 2)))
		 (explodec (car numlist))))
	(hex-to-int (cdr numlist))))))  

;;; (lctouc g_expr)
;;; Returns s-expression formed by translating lower-case alphabetic
;;; characters in <expr> to their upper-case equivalents.
;;; Operates by imploding the translated characters, in the case of a
;;; symbol or string, or by recursively calling on members of a list.
;;; Other object types are returned unchanged.
(defun lctouc (expr)
    (cond
	((dtpr expr) (mapcar 'uctolc expr))
	((or (symbolp expr) (stringp expr))
	 (implode
	     (mapcar
		 '(lambda (ch)
		      (cond ((alphap ch)		; and-out lower-case bit
			     (boole 1 #.(1- (1- #/a)) ch)) (t ch)))
		 (exploden expr))))
	(t expr)))

;;; (log2 x_n)
(defun log2 (n)			; log base 2 (truncated)
  (do ((n (lsh n -1) (lsh n -1))
       (p 0 (1+ p)))
      ((zerop n) p)))

;;; (lowerp sx_char)
(defun lowerp (char)		; is char lower-case alphabetic?
  (cond ((symbolp char)
	 (setq char (car (exploden char)))))
  (and (fixp char)
       (or (and (> char #.(1- #/a))
		(< char #.(1+ #/z))))))

;;; (numericp sx_char)
;;; returns t if char is numeric, otherwise nil
(defun numericp (char)
  (cond ((symbolp char)(setq char (car (exploden char)))))
  (and (fixp char)
       (and (> char #.(1- #/0))
	    (< char #.(1+ #/9)))))

;;; (pad g_item x_n l_list)
;;; Returns <list> padded with copies of <item> to length <n>
(defun pad (item n list)
  (append list (duplicate (- n (length list)) item)))

;;; (pairify l_items)	; make a-list from alternating elements
(defun pairify (pl)
  (pairify* nil pl))
(defun pairify* (rs pl)		; tail-recursive local fun
  (cond (pl (pairify* (cons (list (car pl) (cadr pl)) rs)
		       (cddr pl)))
	(t (nreverse rs))))

;;; (pairifyq l_items)	; make a-list from alternating elements
(defun pairifyq (pl)	; with each second element quoted
  (pairifyq* nil pl))
(defun pairifyq* (rs pl)		; tail-recursive local fun
  (cond (pl (pairifyq* (cons (list (car pl) (kwote (cadr pl))) rs)
		       (cddr pl)))
	(t (nreverse rs))))

;;; (penultimate l_items)	; cdr down to next-to-last list element
(defun penultimate (ls)	
  (cond ((cddr ls) (penultimate (cdr ls)))
	(t ls)))

;;; (split2 l_L)
;;; Splits list <L> into two (new) second-level lists
(defun split2* (L tc1 tc2)
  (cond ((null L) (list (nreverse tc1) (nreverse tc2)))
	(t (split2* (cddr L)
	     (cons (car L) tc1)
	     (cons (cadr L) tc2)))))

(defun split2 (L)
  (split2* L nil nil))

;;; (sublist L IL)
;;; Splits list <L> (destructively) into (length IL) sub-lists.
;;; IL is a list of starting indices, base zero, should be unique positive
;;; fixnums in ascending order, and shouldn't exceed the length of L.
;;; Each resulting sublist <i> begins with (nthcdr (nth <i> IL) L)
(defun sublist (L IL)
  (sublist* 0 nil (cons nil L) IL))
(defun sublist* (I R L IL)		; tail-recursion function
  (cond ((and L IL)
	 (cond
	   ((<& I (car IL))
	    (sublist* (1+ I) R (cdr L) IL))
	   (t (sublist* (1+ I)
			(cons (cdr L) R)
			(prog1 (cdr L) (rplacd L nil))
			(cdr IL)))))
	(t (nreverse R))))

(defun try-fun (fun l-arg)	; try function on each arg until non-nil
  (cond ((funcall fun (car l-arg)))
	(l-arg (try-fun fun (cdr l-arg)))))

;;; (uctolc g_expr)
;;; Returns s-expression formed by translating upper-case alphabetic
;;; characters in <expr> to their lower-case equivalents.
;;; Operates by imploding the translated characters, in the case of a
;;; symbol or string, or by recursively calling on members of a list.
;;; Other object types are returned unchanged.
(defun uctolc (expr)
    (cond
	((dtpr expr) (mapcar 'uctolc expr))
	((or (symbolp expr) (stringp expr))
	 (implode
	     (mapcar
		 '(lambda (ch)
		      (cond ((alphap ch)		; or-in lower-case bit
			     (boole 7 #.(1- #/a) ch)) (t ch)))
		 (exploden expr))))
	(t expr)))

;;; (unique a l) -- Scan <l> for an element <e> "equal" to <a>.
;;; If found, return <e>. Otherwise nconc <a> onto <l>; return <a>.
(defun unique (a l)			; ensure unique in list
  (car
    (do ((cdr_ul l (cdr ul))
	 (ul l cdr_ul))
	((null cdr_ul) (rplacd ul (ncons a)))
	(cond ((equal a (car cdr_ul)) (return cdr_ul))))))

;;; (upperp sx_char)
(defun upperp (char)		; is char upper-case alphabetic?
  (cond ((symbolp char)
	 (setq char (car (exploden char)))))
  (and (fixp char)
       (or (and (> char #.(1- #/A))
		(< char #.(1+ #/Z))))))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; zone.l -- data structures and routines for concrete window zones
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; a "point" is a pair of integer x,y coordinates
;;; a "box" is a pair of points defining lower left and upper right corners
;;; a "position" is a point coupled with a window
;;; a "zone" is a box coupled with a window
;;; a "window" is a machine, integer window id and, for compatibility
;;;	with the toolbox, an integer toolbox window pointer
;;; a "machine" is a name coupled with the j-process-id's of resident servers
;;; The basic idea is to define a notion of a concrete position for a
;;; display object, that can be incorporated into the object data structure.
;;; Higher levels of software can use the objects without explicit reference
;;; to server processes, windows and machines.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare
  (specials t)			; global vars not local to this file
  (macros t))			; compile macros as well

(eval-when (compile)		; trust  to higher level for eval & load
  (load 'utilities)		; utility functions
  (load 'constants)		; common constants for window toolbox
;  (load 'shape)		; arbitrarily shaped screen areas
)

(defstruct
  (position		; a concrete display position
    (:displace t)
    (:list)
    (:conc-name))
  (window (make-window))	; concrete window
  (point (make-point))		; actual x, y coordinates
)

(defstruct
  (zone			; a concrete display zone
    (:displace t)
    (:list)
    (:conc-name))
  (window (make-window))	; concrete window
  (box (make-box))		; bounding box of zone
  (colour W-BACKGROUND)		; colour (for scrolling etc)
  shape
)

(defstruct
  (window		; concrete window
    (:displace t)
    (:list)
    (:conc-name))
  (id 0)			; integer window id
  (machine (make-machine))	; machine (workstation)
  (w 0)				; toolbox window structure pointer
)

(defstruct
  (machine		; machine (workstation)
    (:displace t)
    (:list)
    (:conc-name))
  (name	'unknown-machine)	; machine name
  (servers nil)			; plist of server processes living there
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; manipulation routines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defun add-points (p q)		; vector sum (x1+x2) (y1+y2)
  (make-point
    x (+ (x p) (x q))
    y (+ (y p) (y q))))

(defun subtract-points (p q)	; vector subtract (x1-x2) (y1-y2)
  (make-point
    x (- (x p) (x q))
    y (- (y p) (y q))))

(defun multiply-points (p q)	; vector multiply (x1*x2) (y1*y2)
  (make-point
    x (* (x p) (x q))
    y (* (y p) (y q))))

(defun divide-points (p q)	; vector division (x1-x2) (y1-y2)
  (make-point
    x (/ (x p) (x q))
    y (/ (y p) (y q))))

(defun move-point (p q)		; move point p to point q
  (alter-point p
    x (x q)
    y (y q))
  t)					; return true

(defun box-size (b)		; size of box = ur - ll
  (subtract-points (ur b) (ll b)))

(defun box-interior (b)		; return box just inside this box dimensions
  (make-box
    ll (add-points (ll b) '(1 1))
    ur (subtract-points (ur b) '(1 1))))

(defun move-box (b p)		; move box b to point p (lower-left)
  (let ((size (box-size b)))
       (alter-box b
	 ll p
	 ur (add-points p size))
       t))				; return true

(defun point-in-box (p b)	; is point p in box b? (including boundary)
  (and (>= (x p) (x (ll b)))
       (<= (x p) (x (ur b)))
       (>= (y p) (y (ll b)))
       (<= (y p) (y (ur b)))
  ))

(defun point-in-box-interior (p b) ; is point p in box b? (excluding boundary)
  (and (> (x p) (x (ll b)))
       (< (x p) (x (ur b)))
       (> (y p) (y (ll b)))
       (< (y p) (y (ur b)))
  ))

(defun init-window (w)		; fill in  "window" structure
  (let				; presuming window-w predefined
    ((m (j-machine-name (w-get-manager (window-w w)))))
    (alter-window w id (w-get-id (window-w w)))
    (cond ((not (window-machine w))
	   (alter-window w machine (make-machine name m)))
	  (t (alter-machine (window-machine w) name m)))
    (init-machine (window-machine w))	; also fill in machine structure
    t))				; return true

(defun init-machine (m)		; fill in "machine" structure
  (cond				; presuming machine-name predefined
    ((null (machine-servers m))		; if no plist, make new one
     (alter-machine m servers (ncons 'servers:))))
  (mapc '(lambda (pname)		; for each expected server name
	   (let
	     ((pid (j-search-machine-e jipc-error-code
		     (machine-name m)
		     pname)))		; try to find one on that machine
	     (cond ((j-same-process pid J-NO-PROCESS)
		    (putprop (machine-servers m) nil pname)) ; failed! use nil
		   (t (putprop (machine-servers m) pid pname))))) ; success!
	EXPECTED-WORKSTATION-SERVERS)	; global list of process names
  t)					; return true

(defvar EXPECTED-WORKSTATION-SERVERS	; global list of process names
  '(window_manager creator savemem
     text-composer))			; usually want at least these

(defun window-box (w)		; box fills entire window
  (let ((w-size (w-get-window-size (window-w w))))
       (make-box
	 ll (make-point x 0 y 0)
	 ur (make-point x (car w-size) y (cadr w-size)))
  ))

(defun clear-zone (z colour)	; clear zone (including boundaries)
  (let ((b (box-size (zone-box z))))
       (w-clear-rectangle (window-w (zone-window z))
	 (x (ll (zone-box z))) (y (ll (zone-box z)))
	 (1+ (x b)) (1+ (y b))
	 colour)))

(defun clear-zone-interior (z colour)	; clear zone (excluding boundaries)
  (let ((b (box-size (zone-box z))))
       (w-clear-rectangle (window-w (zone-window z))
	 (1+ (x (ll (zone-box z)))) (1+ (y (ll (zone-box z))))
	 (1- (x b)) (1- (y b))
	 colour)))

(defun pattern-zone (z pattern)	; pattern zone (including boundaries)
  (let ((b (zone-box z)))
       (w-pattern-rectangle (window-w (zone-window z))
	 (x (ll b)) (y (ll b))
	 (1+ (x (ur b))) (1+ (y (ur b))) pattern)
  ))

(defun pattern-zone-interior (z pattern) ; pattern zone (excluding boundaries)
  (let ((b (box-size (zone-box z))))
       (w-pattern-rectangle (window-w (zone-window z))
	 (1+ (x (ll (zone-box z)))) (1+ (y (ll (zone-box z))))
	 (1- (x b)) (1- (y b)) pattern)
  ))

(defun draw-zone-outline (z colour)	; draw zone boundaries
  (let* ((w (window-w (zone-window z)))
	 (b (zone-box z))
	 (ll (ll b))
	 (ur (ur b)))
	(w-draw-vector w (x ll) (y ll) (x ll) (y ur) colour)
	(w-draw-vector w (x ll) (y ur) (x ur) (y ur) colour)
	(w-draw-vector w (x ur) (y ur) (x ur) (y ll) colour)
	(w-draw-vector w (x ur) (y ll) (x ll) (y ll) colour)
  ))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; font.l -- font manipulation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(eval-when (compile)
  (load 'utilities)
  (load 'constants))

(defvar -installed-fonts nil)	; list of installed fonts

(defstruct
  (font			; font structure
    (:displace t)
    (:list)
    (:conc-name))
  (name 'standard)
  (size 8)
  (body 8)
  (cap-height 7)
  (x-height 5)
  (fixed-width 5)
  (first 0)
  (last 127)
  glyph			; the actual characters
)

(defstruct
  (glyph			; glyph structure
    (:displace t)
    (:list)
    (:conc-name))
  code
  width
  (bytes (byte-block 32))	; the actual bitmap
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; 				font manipulation routines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defun read-font (family size path)
  (let ((p (infile path))		; open file
	(x (new-vectori-long 2))
	(f nil))
       (setq f (make-font
		 name family
		 size (tyi p)
		 body (tyi p)
		 cap-height (tyi p)
		 x-height (tyi p)
		 fixed-width (tyi p)
		 first (prog1 (tyi p) (tyi p))
		 last (prog1 (tyi p) (tyi p))))
       (alter-font f glyph
	 (do ((i (font-first f) (1+ i))
	      (r (ncons nil))
	      (g))	   
	     ((> i (font-last f)) (car r))
	     (setq g (make-glyph code i))	; allocate char
	     (do ((j 0 (1+ j)))			; read bitmap
		 ((> j 31))
		 (vseti-byte (glyph-bytes g) j (tyi p)))
	     (alter-glyph g width (tyi p))	; read width
	     (setq r (tconc r g))
	 ))
       (close p)			; close file

       (rplacd				; install font
	 (cond ((assoc (list (font-name f) (font-size f)) -installed-fonts))
	       (t (car (setq -installed-fonts
			     (cons (ncons (list (font-name f) (font-size f)))
				   -installed-fonts)))))
	 f)
       f))				; return font

(def-usage 'read-font '(|'st_family| |'x_size| |'st_path|)
  'l_font-descriptor
  (setq fcn-group (ncons "Font Manipulation:")))

(defun install-font (f)
  (cdr
    (rplacd				; install font
      (cond ((assoc (list (font-name f) (font-size f)) -installed-fonts))
	    (t (car (setq -installed-fonts
			  (cons (ncons (list (font-name f) (font-size f)))
				-installed-fonts)))))
      f)))

(defun find-font (family size)	; always "finds" one even if dummy
  (cond ((cdr (assoc (list family size) -installed-fonts)))
	(t (install-font (make-font name family size size)))))

(def-usage 'find-font
  '(|'st_family| |'x_size|)
  'l_font-descriptor
  fcn-group)

(defun create-font (driver font)
  (j-send-se-list driver
    (list 'make-font
	  (font-name font)
	  (font-size font)
	  (font-body font)
	  (font-cap-height font)
	  (font-x-height font)
	  (font-fixed-width font)
	  (font-first font)
	  (font-last font))))

(defun download-glyph (driver font glyph)
  (j-put-items
    `((J-STRING set-glyph)
       (J-STRING ,(font-name font))
       (J-INT ,(font-size font))
       (J-INT ,(glyph-code glyph))
       (J-INT ,(glyph-width glyph))
       (J-BLOCK ,(glyph-bytes glyph))))
  (j-send driver))

(defun download-font (driver font)
  (do ((g (font-glyph font))
       (font-size (font-size font)))
      ((null g))
      (j-put-items
	`((J-STRING set-glyph)
	  (J-STRING ,(font-name font))
	  (J-INT ,font-size)))
      (do ((gg g (cdr gg)))
	  ((or (null gg) (j-put-items
			   `((J-INT ,(glyph-code (car gg)))
			     (J-INT ,(glyph-width (car gg)))
			     (J-BLOCK
			       ,(glyph-bytes (car gg))
			       ,(+ font-size font-size)))))
	   (setq g gg)))		; when buffer full, save remainder
      (j-send driver)
      (cond ((eq J-STRING (j-next-item-type))
	     (j-gets j-comm-string 128)		; skip past message string
	     (cond ((eq J-INT (j-next-item-type))(patom (j-geti))(terpr)))))
  ))

(def-usage 'download-font
  '(|'x_process-id| |'l_font-descriptor|)
  't
  fcn-group)

(defun read-create-download-font (driver family size path)
  (let ((f (read-font family size path)))
       (create-font driver f)
       (download-font driver f)
       f))

(def-usage 'read-create-download-font
  '(|'x_process-id| |'st_family| |'x_size| |'st_path|)
  'l_font-descriptor
  fcn-group)

(defun font-depth (f)
  (- (font-body f) (font-cap-height f)))

(defun font-height (f)
  (font-cap-height f))

(defun get-font-list (sc) ; arg is string-composer or font-server pid
  (j-send-se sc 'get-font-list)
  (pairify (mapcar
	     '(lambda (x)
		(cond ((stringp (cadr x)) (concat (cadr x)))
		      (t (cadr x))))
	     (j-get-items))))

(defun get-all-font-info (sc) ; arg is string-composer or font-server pid
  (mapc '(lambda (f)
	   (rplacd (apply 'find-font f)
	     (cdr (progn
		    (j-send-se-list sc (cons 'get-font-info f))
		    (mapcar 'cadr (j-get-items))))))
	(get-font-list sc)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; text.l -- fancy text strings
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare
  (specials t)
  (macros t))

(eval-when (compile)
  (load 'utilities)
  (load 'constants)
  (load 'zone)
  (load 'font)
  (load 'look))

(defstruct
  (text		; text structure
    (:displace t)
    (:list)
    (:conc-name))
  (text '||)			; the text to draw
  (look (make-look))		; what style to draw it in
  (kl 0)			; (starting) left kerning mask
  (zone (make-zone))		; specific window, clipping box
  (offset (make-point))		; offset of start point from zone ll
  (kr 0)			; (final) right kerning mask
  (delta (make-point))		; change in (x,y) relative to start point
  (nn -1)			; char count
)

;;; NOTE: clipping box of ((0 0) (-1 -1)) uses window boundaries

(defun text-width (s)		; presumes non-rotated
  (x (text-delta s)))

(defun text-box (s)		; presumes non-rotated
  (make-box
    ll (subtract-points
	 (text-start-point s)
	 (make-point x 0 y (font-depth (look-font (text-look s)))))
    ur (add-points
	 (text-end-point s)
	 (make-point x 0 y (font-height (look-font (text-look s)))))))

(defun text-start-point (s)
  (add-points
    (ll (zone-box (text-zone s)))
    (text-offset s)))

(defun text-end-point (s)
  (add-points
    (text-start-point s)
    (text-delta s)))

(defun text-x (s)	; x coord of start of text object
  (+ (x (ll (zone-box (text-zone s))))
     (x (text-offset s))))

(defun text-y (s)	; y coord of start of text object
  (+ (y (ll (zone-box (text-zone s))))
     (y (text-offset s))))

(defun text-xx (s)	; x coord of end of text object
  (+ (x (ll (zone-box (text-zone s))))
     (x (text-offset s))
     (x (text-delta s))))

(defun text-yy (s)	; y coord of end of text object
  (+ (y (ll (zone-box (text-zone s))))
     (y (text-offset s))
     (y (text-delta s))))

(defun move-text (s p)	; move s to new x,y
  (alter-text s
    offset (subtract-points p (ll (zone-box (text-zone s))))))

(defun draw-text (s)		; quietly draw text, clipping to zone box
  (let (((x y) (text-start-point s))
	(l (text-look s)))
       (j-put-items
	 `((J-STRING compose)
	   (J-INT ,(window-id (zone-window (text-zone s))))
	   (J-STRING ,(text-text s))
	   (J-STRING ,(font-name (look-font l)))
	   (J-INT ,(font-size (look-font l)))
	   (J-INT ,(boole 7 (look-mode l) QUIET))
	   (J-INT ,(look-colour l))
	   (J-INT ,(look-gap l))
	   (J-INT ,(look-ul l))
	   (J-INT ,(text-kl s))
	   (J-INT ,x)
	   (J-INT ,y)
	   (J-INT ,(x (cond
			((zerop (boole 1 ROTATE-180 (look-mode l)))
			 (ur (zone-box (text-zone s))))
			(t (ll (zone-box (text-zone s)))))))
	   (J-INT ,(y (cond
			((zerop (boole 1 ROTATE-90 (look-mode l)))
			 (ur (zone-box (text-zone s))))
			(t (ll (zone-box (text-zone s)))))))
	   (J-INT ,(text-nn s))
	  ))
       (j-send (get (machine-servers
		      (window-machine
			(zone-window
			  (text-zone s))))
		    'text-composer))
  ))

(defun undraw-text (s)	; quietly undraw text, clipping to zone box
  (let (((x y) (text-start-point s))
	(l (text-look s)))
       (j-put-items
	 `((J-STRING compose)
	   (J-INT ,(window-id (zone-window (text-zone s))))
	   (J-STRING ,(text-text s))
	   (J-STRING ,(font-name (look-font l)))
	   (J-INT ,(font-size (look-font l)))
	   (J-INT ,(boole 7 OVERSTRIKE QUIET (look-mode l)))
	   (J-INT ,(inverse-colour (look-colour l)))
	   (J-INT ,(look-gap l))
	   (J-INT ,(look-ul l))
	   (J-INT ,(text-kl s))
	   (J-INT ,x)
	   (J-INT ,y)
	   (J-INT ,(x (cond
			((zerop (boole 1 ROTATE-180 (look-mode l)))
			 (ur (zone-box (text-zone s))))
			(t (ll (zone-box (text-zone s)))))))
	   (J-INT ,(y (cond
			((zerop (boole 1 ROTATE-90 (look-mode l)))
			 (ur (zone-box (text-zone s))))
			(t (ll (zone-box (text-zone s)))))))
	   (J-INT ,(text-nn s))
	  ))
       (j-send (get (machine-servers
		      (window-machine
			(zone-window
			  (text-zone s))))
		    'text-composer))
  ))

(defun format-text (s)	; format text without drawing or clipping
  (let ((memop (symbolp (text-text s)))	; can only memoize symbols
	(k) (p) (q) (l (text-look s)))
       (cond
	 (memop					; are we memoizing? yes!
	   (setq k (unique-look-id l))	; key based on look
	   (setq p (get (text-text s) k))		; alist found on plist
	   (setq q (assoc (text-kl s) p))))		; entry based on kl
       (cond
	 (q (alter-text s			; if info found
	      kr (cadr q)			; record result
	      delta (caddr q)			; then return
	      nn (cadddr q)))
	 (t					; otherwise compute data
	   (j-put-items
	     `((J-STRING compose)
	       (J-INT 0)			; no window needed
	       (J-STRING ,(text-text s))
	       (J-STRING ,(font-name (look-font l)))
	       (J-INT ,(font-size (look-font l)))
	       (J-INT ,(boole 7 NO-DRAW (look-mode l)))
	       (J-INT ,(look-colour l))
	       (J-INT ,(look-gap l))
	       (J-INT ,(look-ul l))
	       (J-INT ,(text-kl s))
	       (J-INT 0)			; starting point 0 0
	       (J-INT 0)
	       (J-INT -1)			; no clipping
	       (J-INT -1)
	       (J-INT -1)
	      ))
	   (j-send (get (machine-servers
			  (window-machine
			    (zone-window
			      (text-zone s))))
			'text-composer))
	   (let ((kr (j-geti))			; now record result
		 (xx (j-geti))
		 (yy (j-geti))
		 (nn (j-geti)))
		(alter-text s
		  kr kr
		  delta (make-point x xx y yy)
		  nn nn)
		(cond (memop				; memoize if req'd
			(cond (p (nconc p
				   (ncons (list (text-kl s) kr
						(text-delta s) nn))))
			      (t (putprop (text-text s)
				   (ncons (list (text-kl s) kr
						(text-delta s) nn))
				   k))))
		))
	 ))
       't))					; always return t

(defun scan-text (s p) ; scan text s for point p, return (kr delta nn)
  (let (((x y) (text-start-point s))		; inside: check text
	(l (text-look s)))
       (j-put-items
	 `((J-STRING compose)
	   (J-INT 0)
	   (J-STRING ,(text-text s))
	   (J-STRING ,(font-name (look-font l)))
	   (J-INT ,(font-size (look-font l)))
	   (J-INT ,(boole 7 NO-DRAW (look-mode l)))
	   (J-INT ,(look-colour l))
	   (J-INT ,(look-gap l))
	   (J-INT ,(look-ul l))
	   (J-INT ,(text-kl s))
	   (J-INT ,x)
	   (J-INT ,y)
	   (J-INT ,(x p))
	   (J-INT ,(y p))
	   (J-INT ,(text-nn s))
	  ))
       (j-send (get (machine-servers
		      (window-machine
			(zone-window
			  (text-zone s))))
		    'text-composer))
       (let ((kr (j-geti))			; now record result
	     (xx (j-geti))
	     (yy (j-geti))
	     (nn (j-geti)))
	    (list kr (make-point x (- xx x) y (- yy y)) nn))
  ))

(defun format-draw-text (s)		; draw it while formatting
  (let ((memop (symbolp (text-text s)))	; can only memoize symbols
	((x y) (text-start-point s))
	(k) (p) (q) (l (text-look s)))
       (cond
	 (memop					; are we memoizing? yes!
	   (setq k (unique-look-id l))	; key based on look
	   (setq p (get (text-text s) k))		; alist found on plist
	   (setq q (assoc (text-kl s) p))))		; entry based on kl
       (cond
	 (q (alter-text s			; if info found
	      kr (cadr q)			; record result
	      delta (caddr q)
	      nn (cadddr q))
	    (draw-text s))			; draw it & return
	 (t					; otherwise compute data
	   (j-put-items
	     `((J-STRING compose)
	       (J-INT ,(window-id (zone-window (text-zone s))))
	       (J-STRING ,(text-text s))
	       (J-STRING ,(font-name (look-font l )))
	       (J-INT ,(font-size (look-font l)))
	       (J-INT ,(boole 4 (look-mode l) QUIET))
	       (J-INT ,(look-colour l))
	       (J-INT ,(look-gap l))
	       (J-INT ,(look-ul l))
	       (J-INT ,(text-kl s))
	       (J-INT ,x)
	       (J-INT ,y)
	       (J-INT ,(x (cond
			    ((zerop (boole 1 ROTATE-180 (look-mode l)))
			     (ur (zone-box (text-zone s))))
			    (t (ll (zone-box (text-zone s)))))))
	       (J-INT ,(y (cond
			    ((zerop (boole 1 ROTATE-90 (look-mode l)))
			     (ur (zone-box (text-zone s))))
			    (t (ll (zone-box (text-zone s)))))))
	       (J-INT -1)			; format to end of text
	      ))
	   (j-send (get (machine-servers
			  (window-machine
			    (zone-window
			      (text-zone s))))
			'text-composer))
	   (let ((kr (j-geti))			; now alter result data
		 (xx (j-geti))
		 (yy (j-geti))
		 (nn (j-geti)))
		(cond ((neq nn (length (exploden (text-text s))))
		       (format-text s))	; actually clipped! reformat
		      (t (alter-text s
			   kr kr
			   delta (make-point x (- xx x) y (- yy y))
			   nn nn)
			 (cond
			   (memop		; memoize if req'd
			     (cond (p (nconc p
					(ncons (list (text-kl s) kr
						     (text-delta s) nn))))
				   (t (putprop (text-text s)
					(ncons (list (text-kl s) kr
						     (text-delta s) nn))
					k))))
			 ))
		))
	 ))
       't))					; always return t

(defun backspace-text (s n)	; undraw last n characters, remove from text
  (cond				; this presumes s has valid delta,kr,nn
    ((plusp (text-nn s))	; proceed only if length > 0
     (setq n (min n (text-nn s)))	; can't delete more than nn chars
     (let ((text (text-text s))
	   (l (text-look s)))
	  (alter-text s		; keep all but last n chars
	    text (substring text 1 (- (text-nn s) n))
	    nn (- (text-nn s) n))
	  (format-text s)		; reformat to find the new end
	  (j-put-items
	    `((J-STRING compose)	; now undraw last character
	      (J-INT ,(window-id (zone-window (text-zone s))))
	      (J-STRING ,(substring text (- n))) ; undraw last n chars
	      (J-STRING ,(font-name (look-font l)))
	      (J-INT ,(font-size (look-font l)))
	      (J-INT ,(boole 7 QUIET OVERSTRIKE (look-mode l)))
	      (J-INT ,(inverse-colour (look-colour l)))
	      (J-INT ,(look-gap l))
	      (J-INT ,(look-ul l))
	      (J-INT ,(text-kr s))
	      (J-INT ,(text-xx s))
	      (J-INT ,(text-yy s))
	      (J-INT ,(x (cond
			   ((zerop (boole 1 ROTATE-180 (look-mode l)))
			    (ur (zone-box (text-zone s))))
			   (t (ll (zone-box (text-zone s)))))))
	      (J-INT ,(y (cond
			   ((zerop (boole 1 ROTATE-90 (look-mode l)))
			    (ur (zone-box (text-zone s))))
			   (t (ll (zone-box (text-zone s)))))))
	      (J-INT ,n)
	     ))
	  (j-send (get (machine-servers
			 (window-machine
			   (zone-window
			     (text-zone s))))
		       'text-composer))
	  't))			; return t if able to do it; nil if nn <= 0
  ))

(defun append-text (s c)	; draw new char(s) & add to end of text
  (cond ((fixp c)		; this presumes s has valid delta,kr,nn
	 (setq c (ascii c))))
  (j-put-items
    `((J-STRING compose)	; draw new last character(s)
      (J-INT ,(window-id (zone-window (text-zone s))))
      (J-STRING ,c)
      (J-STRING ,(font-name (look-font (text-look s))))
      (J-INT ,(font-size (look-font (text-look s))))
      (J-INT ,(boole 4 (look-mode (text-look s)) QUIET))	; be noisy!
      (J-INT ,(look-colour (text-look s)))
      (J-INT ,(look-gap (text-look s)))
      (J-INT ,(look-ul (text-look s)))
      (J-INT ,(text-kr s))	; this presumes s has valid delta,kr,nn
      (J-INT ,(text-xx s))
      (J-INT ,(text-yy s))
	       (J-INT ,(x (cond
			    ((zerop (boole 1 ROTATE-180 (look-mode l)))
			     (ur (zone-box (text-zone s))))
			    (t (ll (zone-box (text-zone s)))))))
	       (J-INT ,(y (cond
			    ((zerop (boole 1 ROTATE-90 (look-mode l)))
			     (ur (zone-box (text-zone s))))
			    (t (ll (zone-box (text-zone s)))))))
      (J-INT -1)
     ))
  (j-send (get (machine-servers
		 (window-machine
		   (zone-window
		     (text-zone s))))
	       'text-composer))
  (let ((kr (j-geti))
	(xx (j-geti))
	(yy (j-geti))
	(nn (j-geti)))
       (alter-text s
	 text (concat (text-text s) c)
	 kr kr
	 delta (subtract-points
		 (make-point x xx y yy)
		 (text-start-point s))
	 nn (+ (text-nn s) nn)))
  't)

(defun append-text-scroll (s c colour) ; draw and add new char(s)
  (let ((w (window-id	;  while scrolling zone box b in specified colour
	     (zone-window (text-zone s))))
	(b (zone-box (text-zone s)))
	(l (text-look s)))
       (cond ((fixp c)
	      (setq c (ascii c)))) ; this presumes s has valid delta,kr,nn
       (j-put-items
	 `((J-STRING compose)	; format new last character
	   (J-INT ,w)
	   (J-STRING ,c)
	   (J-STRING ,(font-name (look-font l)))
	   (J-INT ,(font-size (look-font l)))
	   (J-INT ,(boole 7 NO-DRAW (look-mode l)))
	   (J-INT ,(look-colour l))
	   (J-INT ,(look-gap l))
	   (J-INT ,(look-ul l))
	   (J-INT ,(text-kr s)) ; this presumes s has valid delta,kr,nn
	   (J-INT 0)
	   (J-INT 0)
	   (J-INT -1)
	   (J-INT -1)
	   (J-INT -1)
	  ))
       (j-send (get (machine-servers
		      (window-machine
			(zone-window
			  (text-zone s))))
		    'text-composer))
       (let ((kr (j-geti))
	     (xx (j-geti))
	     (yy (j-geti))
	     (nn (j-geti)))
	    (apply
	      'w-scroll-rectangle
	      (nconc
		(ncons (window-w (zone-window (text-zone s))))
		(let ((direction (boole 1 ROTATION
					(look-mode l))))
		     (cond
		       ((= direction ROTATE-0)
			(list (text-xx s)
			      (y (ll b))
			      (- (x (ur b)) (text-xx s) -1)
			      (- (y (ur b)) (y (ll b)) -1)
			      WM-RIGHT xx))
		       ((= direction ROTATE-90)
			(list (x (ll b))
			      (text-yy s)
			      (- (x (ur b)) (x (ll b)) -1)
			      (- (y (ur b)) (text-yy s) -1)
			      WM-UP yy))
		       ((= direction ROTATE-180)
			(list (x (ll b))
			      (y (ll b))
			      (- (text-xx s) (x (ll b)) -1)
			      (- (y (ur b)) (y (ll b)) -1)
			      WM-LEFT (- xx)))
		       ((= direction ROTATE-270)
			(list (x (ll b))
			      (y (ll b))
			      (- (x (ur b)) (x (ll b)) -1)
			      (- (text-yy s) (y (ll b)) -1)
			      WM-DOWN (- yy)))
		     ))
		(ncons colour)))
	    (w-flush (window-w (zone-window (text-zone s))))
	    (j-put-items
	      `((J-STRING compose)	; draw new last character
		(J-INT ,w)
		(J-STRING ,c)
		(J-STRING ,(font-name (look-font l)))
		(J-INT ,(font-size (look-font l)))
		(J-INT ,(boole 7 (look-mode l) QUIET))
		(J-INT ,(look-colour l))
		(J-INT ,(look-gap l))
		(J-INT ,(look-ul l))
		(J-INT ,(text-kr s)) ; this presumes s has valid delta,kr,nn
		(J-INT ,(text-xx s))
		(J-INT ,(text-yy s))
	       (J-INT ,(x (cond
			    ((zerop (boole 1 ROTATE-180 (look-mode l)))
			     (ur (zone-box (text-zone s))))
			    (t (ll (zone-box (text-zone s)))))))
	       (J-INT ,(y (cond
			    ((zerop (boole 1 ROTATE-90 (look-mode l)))
			     (ur (zone-box (text-zone s))))
			    (t (ll (zone-box (text-zone s)))))))
		(J-INT -1)
	       ))
	    (j-send (get (machine-servers
			   (window-machine
			     (zone-window
			       (text-zone s))))
			 'text-composer))
	    (alter-text s
	      text (concat (text-text s) c)
	      kr kr
	      delta (add-points
		      (make-point x xx y yy)
		      (text-delta s))
	      nn (+ (text-nn s) nn))
       )'t))

(defun format-text-list (sl)			; chain the text objects
  (do ((s (car sl) (car sl))			; so that xx,yy,kr of one
       (sl (cdr sl) (cdr sl)))			; used as x,y,kl of next
      ((null sl) (format-text s) 't)
      (format-text s)
      (alter-text (car sl)
	kl (text-kr s))
      (move-text (car sl) (text-end-point s))
  ))

(defun move-text-list (sl p)	; move whole list of text objects
  (do ((s (car sl) (car sl))
       (sl (cdr sl) (cdr sl))
       (p p (text-end-point s)))
      ((null s) 't)
      (move-text s p)
  ))

(defun compress-text-list (sl)		; combine like-moded text objects
  (do ((s (car sl) (car sl))			; to reduce communication
       (sl (cdr sl) (cdr sl))
       (new-text nil)
       (new-end-point (text-start-point s))
       (new-s (append (car sl) nil))	; top-level copy
       (dx nil)
       (gap (look-gap (text-look (car sl))))
       (result nil))
      ((null s) (alter-text new-s
		   text (apply 'concat (nreverse new-text))
		   nn -1)
       (nreverse (cons new-s result)))		; return new s-list
      (setq dx (- (x (text-start-point s))
		  (x new-end-point)))
      (cond ((and			; check most likely diffs first
	       (or (eq dx 0) (>= dx (look-gap (text-look s))))
	       (= (y (text-start-point s)) (y new-end-point))
	       (eq (text-look s)
		   (text-look new-s))
	     )				; presume kerning doesn't matter!
	     (cond ((plusp dx)		; horizontal movement
		    (setq new-text
			  (cons
			    (implode
			      (do ((dx (- dx gap 4) (- dx gap 4))
				   (result nil))
				  ((minusp dx)
				   (do ((dx (+ dx 4 -1) (- dx gap 1)))
				       ((minusp dx)
					(cond ((eq dx -1)
					       (setq result
						     (cons 1 result)))))
							; 0-pixel space
				       (setq result (cons 2 result)))
							; 1-pixel space
				   result)
				  (setq result (cons 3 result))
							; 4-pixel space
			      ))
			    new-text))))
	     (setq new-text (cons (text-text s) new-text))
	     (setq new-end-point (text-end-point s))
	    )
	    (t (alter-text new-s
		 text (apply 'concat (nreverse new-text))
		 nn -1
		 delta (subtract-points new-end-point
			 (text-start-point new-s)))
	       (setq result (cons new-s result))
	       (setq new-s (append s nil)
		     new-text (ncons (text-text s)))
	       (setq
		 new-end-point (text-start-point s)
		 gap (look-gap (text-look s)))
	    )
      )))

(defun draw-text-list (sl)
  (mapc '(lambda (x) (draw-text x)) sl) 't)

(defun undraw-text-list (sl)
  (mapc '(lambda (x) (undraw-text x)) sl) 't)

(defun format-draw-text-list (slist) ; format all on same line
  (do ((s (car slist) (car sl))
       (sl (cdr slist) (cdr sl)))
      ((null sl) (format-draw-text s))	; format the last one
      (format-draw-text s)
      (move-text (car sl)	; chain xx,yy,kr to next one's x,y,kl
	(text-end-point s))
  ))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; text-edit.l -- rudimentary line editor for fancy character texts
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;; These routines provide a simple line editor with control keys reminiscent
;;; of the default EMACS key bindings.
;;;
;;; The calling program presumably has obtained a "point" event, at
;;; position "p".  The cursor will be placed on the nearest character,
;;; and then input is accepted from the keyboard, until such time as a
;;; <return> key is accepted, or a point event occurs outside the text
;;; zone boundary, or until a non-key, non-point event occurs.  Another
;;; point event within the text zone causes the cursor to be re-positioned.
;;;
;;; Editing operations currently supported are:
;;;	CTRL-A (ascii 1)	; control A = beginning of line
;;;	CTRL-B (ascii 2)	; control B = backward-character
;;;	CTRL-D (ascii 4)	; control D = delete next char
;;;	CTRL-E (ascii 5)	; control E = end of line
;;;	CTRL-F (ascii 6)	; control F = forward-character
;;;	BACKSPACE (ascii 8)	; BACKSPACE = delete previous char
;;;	CTRL-K (ascii 11)	; control K = kill to end of line
;;;	CTRL-L (ascii 12)	; control L = redraw text
;;;	RETURN (ascii 13)	; RETURN = "done"
;;;	CTRL-T (ascii 20)	; control T = transpose previous 2 chars
;;;	CTRL-Y (ascii 25)	; control Y = "yank" recently killed text

(declare
  (specials t)
  (macros t))

(eval-when (compile)
  (load 'utilities)
  (load 'constants)
  (load 'zone)
  (load 'font)
  (load 'look)
  (load 'text))


(eval-when (compile eval load)
  (defvar BACKSPACE (ascii 8))	; backspace char = delete previous char
  (defvar RETURN (ascii 13))	; carriage return = "done"
  (defvar CTRL-A (ascii 1))	; control A = beginning of line
  (defvar CTRL-B (ascii 2))	; control B = backward-character
  (defvar CTRL-D (ascii 4))	; control D = delete next char
  (defvar CTRL-E (ascii 5))	; control E = end of line
  (defvar CTRL-F (ascii 6))	; control F = forward-character
  (defvar CTRL-K (ascii 11))	; control K = kill to end of line
  (defvar CTRL-L (ascii 12))	; control L = redraw text
  (defvar CTRL-T (ascii 20))	; control T = transpose previous 2 chars
  (defvar CTRL-Y (ascii 25))	; control Y = "yank" recently killed text
  (defvar TYPEAHEAD-THRESHOLD 5); can type at most 5 chars -> forced feedback
)

(defun edit-text (s p)	; edit a text at point p
  (cond					; p outside zone => nil
    ((not (point-in-box p (zone-box (text-zone s)))) nil)
    (t					; p inside zone => edit text
      (let
	((w (window-w (zone-window (text-zone s))))
	 (post (append s nil))
	 (kill-text ""))
	(split-texts s post p)	; split into left and right parts
	(draw-cursor-leading-text post)	; highlight first char
	(skip-stroke-release-events w)
	(do ((e (w-get-next-event w)		; get an event
		(w-get-next-event w))		; then keep getting events
	     (l) (c))				; character list, character
	    ((eq c '#.RETURN)		; stop when <return> is received
	     (cond ((neq e WM-KEY)	; if not caused by key, put event back
		    (w-put-back-event w)))
	     (combine-texts s post)
	     t)			; just return 't
	    (cond			; main loop
	      ((eq e WM-KEY)
	       (setq c (concat (car (w-get-key w))))	; get the character
	       (cond
		 ((eq c '#.BACKSPACE)		; backspace char
		  (text-delete-previous-character s post))
		 ((eq c '#.CTRL-A)			; control A
		  (text-beginning-of-line s post))
		 ((eq c '#.CTRL-B)			; control B
		  (text-backward-character s post))
		 ((eq c '#.CTRL-D)			; control D
		  (text-delete-next-character s post))
		 ((eq c '#.CTRL-E)			; control E
		  (text-end-of-line s post))
		 ((eq c '#.CTRL-F)			; control F
		  (text-forward-character s post))
		 ((eq c '#.CTRL-K)			; control K
		  (text-kill-to-end-of-line s post))
		 ((eq c '#.CTRL-L)			; control L
		  (text-redraw-display s post))
		 ((eq c '#.CTRL-T)			; control T
		  (text-transpose-characters s post))
		 ((eq c '#.CTRL-Y)			; control Y
		  (text-yank-from-killbuffer s post))
		 ((neq c '#.RETURN)			; not <return>
		  (text-insert-character s post))
		 (t (w-put-back-event w))	; it's a <return>; put it back
	       ))			; so loop control can get it again
	      ((eq e WM-POINT-DEPRESSED)
	       (setq p (w-get-point w))
	       (cond				; check point in zone
		 ((point-in-box p (zone-box (text-zone s)))
		  (draw-cursor-leading-text post)	; un-highlight char
		  (combine-texts s post)
		  (split-texts s post p)
		  (draw-cursor-leading-text post)	; highlight new char
		  (skip-stroke-release-events w))
		 (t (w-put-back-event w)	; outside zone => return
		    (setq c '#.RETURN))))
	      ((neq e WM-CANCEL)		; an event we can't handle
	       (w-put-back-event w)		; so put it back, then return
	       (setq c '#.RETURN))
	    )))
    )))

(defun input-typeahead-keys (w n brk-fcn l)	; return keys typed ahead
   (cond					; brk-fcn tests text
     ((or (zerop n)				; already have max typeahead
	  (not (w-any-events w))) (nreverse l))	; or there aren't any events
     (t (let ((x (w-get-next-event w)))		; there's an event
	     (cond
	       ((neq x WM-KEY)
		(w-put-back-event w) (nreverse l))	; but not a keystroke
	       (t (setq x (car (w-get-key w)))		; it's a keystroke
		  (cond
		    ((funcall brk-fcn x)		; is it a break char?
		     (w-put-back-event w) (nreverse l))	; it's a special char
		    (t (input-typeahead-keys		; it's a regular char
			 w (1- n) brk-fcn (cons x l)))	; tail recur for rest
		  )))))))

(defun split-texts (s post p)		; split text s at point p
  (let					; yielding texts s and post
    (((kr delta nn) (scan-text s p)))	; scan for char pos'n
    (alter-text post			; text incl & after char pt'ed
      text (cond ((substring (text-text s) (1+ nn)))	; if it exists!
		 (""))			; otherwise,nothing
      offset (add-points (text-offset s) delta)
      kl kr
      delta (subtract-points (text-delta s) delta)
      nn (- (text-nn s) nn))
    (alter-text s kr kr delta delta nn nn	; truncate text
      text (cond ((substring (text-text s) 1 nn))
		 ("")))
  ))

(defun skip-stroke-release-events (w)
  (do ((e (w-get-next-event w)
	  (w-get-next-event w)))
      ((neq e WM-POINT-STROKE)		; get events until non-point-stroke
       (cond ((neq e WM-POINT-RELEASED)	; should be point-release
	      (w-put-back-event w))))	; if not, put it back
  ))

(defun combine-texts (s post)	; recombine texts
  (alter-text s
    text (concat (text-text s) (text-text post))
    nn (+ (text-nn s) (text-nn post))
    delta (add-points (text-delta s) (text-delta post))
    kr (text-kr post))
  (format-text s))

(defun draw-cursor-leading-text (s)	; highlight first char of text
  (let ((c (append s nil)))
       (alter-text c			; get first char
	 text (concat (cond ((substring (text-text c) 1 1))	; if any
			    (t 'a))))	; otherwise use a typical character
       (format-text c)
       (w-clear-rectangle
	 (window-w (zone-window (text-zone c)))
	 (text-x c)
	 (y (ll (zone-box (text-zone c))))
	 (min (x (text-delta c))
	      (- (x (ur (zone-box (text-zone c))))
		 (text-x c) -1))
	 (- (y (ur (zone-box (text-zone c))))
	    (y (ll (zone-box (text-zone c)))) -1)
	 W-XOR)
       (w-flush (window-w (zone-window (text-zone c))))
       t))

(defun text-delete-previous-character (s post)
  (let ((l (input-typeahead-keys w TYPEAHEAD-THRESHOLD
	     '(lambda (x)	; break on first non-BS
		(not (equal x #.(get_pname BACKSPACE))))
	     (ncons '#.BACKSPACE))))
       (alter-text s
	 nn (max 0 (- (text-nn s) (length l))))
       (alter-text s
	 text (cond ((substring
		       (text-text s)
		       1 (text-nn s)))
		    ("")))
       (format-text s)
       (w-scroll-rectangle
	 (window-w (zone-window (text-zone s)))
	 (text-xx s)
	 (y (ll (zone-box (text-zone s))))
	 (- (x (ur (zone-box (text-zone s))))
	    (text-xx s) 1)
	 (1+ (y (box-size (zone-box (text-zone s)))))
	 WM-LEFT
	 (- (x (text-start-point post))
	    (x (text-end-point s)))
	 (zone-colour (text-zone s)))
       (w-flush
	 (window-w (zone-window (text-zone s))))
       (move-text post (text-end-point s))
       (alter-text post kl (text-kr s))))

(defun text-beginning-of-line (s post)
  (draw-cursor-leading-text post)	; un-highlight first char
  (alter-text post
    text (concat (text-text s) (text-text post))
    nn (+  (text-nn s) (text-nn post))
    delta (add-points (text-delta s) (text-delta post))
    kl 0
    offset (text-offset s))
  (alter-text s text "" nn 0 delta '(0 0) kr 0)
  (draw-cursor-leading-text post))	; highlight new first char

(defun text-backward-character (s post)
  (let ((l (input-typeahead-keys w TYPEAHEAD-THRESHOLD
	     '(lambda (x)	; break on first non-BS
		(not (equal x #.(get_pname CTRL-B))))
	     (ncons '#.CTRL-B))))
       (draw-cursor-leading-text post)	; un-highlight first char
       (alter-text post
	 text (get_pname (concat (substring (text-text s) (- (length l)))
			   (text-text post)))
	 nn (1+ (text-nn post)))
       (alter-text s
	 text (substring (text-text s) 1 (- (text-nn s) (length l)))
	 nn (- (text-nn s) (length l)))
       (format-text s)
       (alter-text post
	 kl (text-kr s)
	 offset (add-points (text-offset s) (text-delta s))
	 delta (subtract-points
		 (text-end-point post)
		 (text-end-point s)))
       (draw-cursor-leading-text post)	; highlight new first char
  ))

(defun text-forward-character (s post)
  (let ((l (input-typeahead-keys w TYPEAHEAD-THRESHOLD
	     '(lambda (x)	; break on first non-BS
		(not (equal x #.(get_pname CTRL-F))))
	     (ncons '#.CTRL-F))))
       (draw-cursor-leading-text post)	; un-highlight first char
       (alter-text s
	 text (get_pname (concat (text-text s)
			   (substring (text-text post) 1 (length l))))
	 nn (+ (text-nn s) (length l)))
       (format-text s)
       (alter-text post
	 text (substring (text-text post) (1+ (length l)))
	 nn (- (text-nn post) (length l))
	 kl (text-kr s)
	 offset (add-points (text-offset s) (text-delta s))
	 delta (subtract-points
		 (text-end-point post)
		 (text-end-point s)))
       (draw-cursor-leading-text post)	; highlight new first char
  ))

(defun text-end-of-line (s post)
  (draw-cursor-leading-text post)	; un-highlight first char
  (alter-text s
    text (concat (text-text s) (text-text post))
    nn (+  (text-nn s) (text-nn post))
    delta (add-points (text-delta s) (text-delta post))
    kr (text-kr post))
  (alter-text post
    text ""
    nn 0
    offset (add-points (text-offset post) (text-delta post))
    delta '(0 0)
    kl (text-kr s))
  (draw-cursor-leading-text post))	; highlight new first char
  
(defun text-kill-to-end-of-line (s post)
  (w-clear-rectangle
    (window-w (zone-window (text-zone post)))
    (text-x post)
    (y (ll (zone-box (text-zone post))))
    (- (x (ur (zone-box (text-zone post)))) (text-x post))
    (1+ (y (box-size (zone-box (text-zone post)))))
    (zone-colour (text-zone post)))
  (setq kill-text (text-text post))
  (alter-text post
    text ""
    nn 0
    delta '(0 0)
    kl (text-kr s))
  (draw-cursor-leading-text post))	; highlight new first char
  
(defun text-yank-from-killbuffer (s post)
  (append-text-scroll s kill-text
    (zone-colour (text-zone s)))
  (move-text post (text-end-point s))
  (alter-text post
    kl (text-kr s)))

(defun text-transpose-characters (s post)
  (let ((tmp (append s nil)))
       (alter-text tmp
	 nn (- (text-nn tmp) 2))
       (let (((kr delta nn) (scan-text tmp '(-1 -1)))) ; find 2nd prev char
	    (alter-text tmp
	      text (substring (text-text tmp) -2)
	      offset (add-points (text-offset tmp) delta)
	      kl kr)
	    (format-text tmp)
	    (w-clear-rectangle
	      (window-w (zone-window (text-zone tmp)))
	      (text-x tmp)
	      (y (ll (zone-box (text-zone tmp))))
	      (x (text-delta tmp))
	      (1+ (y (box-size (zone-box (text-zone tmp)))))
	      (zone-colour (text-zone tmp)))
	    (w-flush (window-w (zone-window (text-zone tmp))))
	    (alter-text tmp
	      text (get_pname (concat
				(substring (text-text tmp) 2 1)
				(substring (text-text tmp) 1 1))))
	    (format-draw-text tmp)
	    (alter-text s
	      text (get_pname
		     (concat
		       (substring (text-text s) 1 (- (text-nn s) 2))
		       (text-text tmp)))
	      kr (text-kr tmp))
       )))

(defun text-delete-next-character (s post)
  (let ((l (input-typeahead-keys w TYPEAHEAD-THRESHOLD
	     '(lambda (x)	; break on first non-BS
		(not (equal x #.(get_pname CTRL-D))))
	     (ncons '#.CTRL-D))))
       (alter-text post
	 nn (length l))
       (let (((kl delta nn)		; scan for nn'th char position
	      (scan-text post '(-1 -1))))
	    (w-scroll-rectangle
	      (window-w (zone-window (text-zone post)))
	      (text-x post)
	      (y (ll (zone-box (text-zone post))))
	      (- (x (ur (zone-box (text-zone post))))
		 (text-x post) 1)
	      (1+ (y (box-size (zone-box (text-zone post)))))
	      WM-LEFT
	      (x delta)
	      (zone-colour (text-zone post)))
	    (alter-text post
	      nn (max 0 (- (length (exploden (text-text post)))
			   (length l)))
	      kl kl)
	    (alter-text post
	      text (cond ((substring
			    (text-text post)
			    (- (text-nn post))))
			 ("")))
	    (format-text post)
	    (draw-cursor-leading-text post)
	    (w-flush (window-w (zone-window (text-zone post))))
       )))

(defun text-insert-character (s post)
  (let ((l (input-typeahead-keys w TYPEAHEAD-THRESHOLD
	     '(lambda (x)	; break on first BS or CR
		(memq (concat x) '#.(list BACKSPACE RETURN)))
	     (ncons c))))
       (append-text-scroll s (concatl l)
	 (zone-colour (text-zone s)))
       (move-text post (text-end-point s))
       (alter-text post
	 kl (text-kr s))))

(defun text-redraw-display (s post)
  (clear-zone (text-zone s) (zone-colour (text-zone s)))
  (w-flush (window-w (zone-window (text-zone post))))
  (format-draw-text s)
  (alter-text post
    kl (text-kr s)
    offset (add-points (text-offset s) (text-delta s)))
  (format-draw-text post)
  (draw-cursor-leading-text post))
