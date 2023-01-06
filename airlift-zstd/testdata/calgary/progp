program interval(input,output);
{dataflow simulator for real interval arithmetic}

const
	IMem = 500; {number of instructions}
	DMem = 200;  {number of interval variables allowed}
	Par = 3;   {max number of paramters for an operator}
	Maxexp = 10; Minexp = -9;
	Maxinf =10000; Mininf =-10000; {Tied to values of Minman and Maxman}
	Maxman = 9999; Minman = -9999; {cannot exceed sqrt(maxint)}
	Splitman = 1000; {Smallest normalized mantisa}
	Digits = 4; {number of digits in mantissa}

type
	Positive= 0..maxint;
	State  = -1..99;  {Used for holding state of operator -1:done}
	OpType = (print,pr,tr,soln,readr,halve,halves,linh,mult,add,intgr,
less,leq,noteq,sqrr,minr,maxr,modu,absr,trig,expr,lb,ub,copy,stop); {!!}
	Ptr    = 1..DMem;
	Loc    = 1..IMem;
	Loc0   = 0..IMem;
	EdgeT  = (hout,lin,hin,lout); {Warning this order is important in}
				      {predicates such as gtS,geS}
	CardT  = (finite,infinite);
	ExpT   = Minexp..Maxexp;
	ManT   = Mininf..Maxinf; 
	Pflag  = (PNull,PSoln,PTrace,PPrint);
	Sreal  = record
		    edge:EdgeT;
		    cardinality:CardT;
		    exp:ExpT; {exponent}
		    mantissa:ManT;
		 end;
	Int    = record
		    hi:Sreal;
		    lo:Sreal;
	 end;
	Instr  = record
		    Code:OpType;
		    Pars: array[0..Par] of 0..DMem;
		 end;
	DataMem= record
		    D        :array [Ptr] of Int;
		    S        :array [Loc] of State;
		    LastHalve:Loc;
		    RHalve   :array [Loc] of real;
		 end;
	DataFlags=record
		    PF	     :array [Ptr] of Pflag;
		 end;
var
	Debug  : (none,activity,post,trace,dump);
	Cut    : (once,all);
	GlobalEnd,Verifiable:boolean;
	HalveThreshold:real;
	I      : array [Loc] of Instr; {Memory holding instructions}
	End    : Loc; {last instruction in I}
	ParN   : array [OpType] of -1..Par; {number of parameters for each 
			opcode. -1 means no result}
        ParIntersect : array [OpType] of boolean ;
	DInit  : DataMem; {initial memory which is cleared and 
				used in first call}
	DF     : DataFlags; {hold flags for variables, e.g. print/trace}
	MaxDMem:0..DMem;
	Shift  : array[0..Digits] of 1..maxint;{array of constant multipliers}
						{used for alignment etc.}
	Dummy  :Positive;
	{constant intervals and Sreals}
	PlusInfS,MinusInfS,PlusSmallS,MinusSmallS,ZeroS,
	PlusFiniteS,MinusFiniteS:Sreal;
	Zero,All,AllFinite:Int;

procedure deblank;
var Ch:char;
begin
   while (not eof) and (input^ in [' ','	']) do read(Ch);
end;

procedure InitialOptions;

#include '/user/profs/cleary/bin/options.i';

   procedure Option;
   begin
      case Opt of
      'a','A':Debug:=activity;
      'd','D':Debug:=dump;
      'h','H':HalveThreshold:=StringNum/100;
      'n','N':Debug:=none;
      'p','P':Debug:=post;
      't','T':Debug:=trace;
      'v','V':Verifiable:=true;
      end;
   end;

begin
   Debug:=trace;
   Verifiable:=false;
   HalveThreshold:=67/100;
   Options;
   writeln(Debug);
   writeln('Verifiable:',Verifiable);
   writeln('Halve threshold',HalveThreshold);
end;{InitialOptions}

procedure NormalizeUp(E,M:integer;var S:Sreal;var Closed:boolean);
begin
with S do
begin
   if M=0 then S:=ZeroS else
   if M>0 then
   begin
      while M>=Maxinf do
      begin 
	 if M mod 10 > 0 then begin Closed:=false;M:=(M div 10)+1 end
	 else M:=M div 10;
	 E:=E+1;
      end;
	 
      while M < Maxinf div 10 do
      begin M:=M*10; E:=E-1; 
      end;

      if E > Maxexp then {overflow-set to infinity}
      begin 
	 S:=PlusInfS;
	 Closed:=false;
      end else
      if E < Minexp then {underflow-set to smallest positive value}
      begin 
         S:=PlusSmallS;
         Closed:=false;
      end else
      begin cardinality:=finite;exp:=E;mantissa:=M;
      end;
   end else	 
   if M < 0 then
   begin
      while M <= Mininf do
      begin 
	 if M mod 10 < 0 then Closed:=false else
	 if M mod 10 > 0 then halt;
	 M:=M div 10;
	 E:=E+1;
      end;
	 
      while M > (Mininf div 10) do
      begin M:=M*10; E:=E-1; 
      end;

      if E > Maxexp then {overflow-set to most negative value}
      begin 
         S:=MinusFiniteS;
         Closed:=false;
      end 
      else
      if E < Minexp then {underflow-set to zero}
      begin
         S:=ZeroS;
         Closed:=false;
      end else
      begin
         cardinality:=finite;exp:=E;mantissa:=M;
      end;
   end;
end;
end;{NormalizeUp}

procedure NormalizeDn(E,M:integer;var S:Sreal;var Closed:boolean);
begin
with S do
begin
   if M=0 then S:=ZeroS else
   if M>0 then
   begin
      while M >= Maxinf do
      begin 
	 if M mod 10 > 0 then Closed:=false else
	 if M mod 10 < 0 then halt;
	 M:=M div 10;
	 E:=E+1;
      end;
	 
      while (M < Maxinf div 10) do
      begin M:=M*10; E:=E-1; 
      end;

      if E > Maxexp then {overflow-set to largest positive value}
      begin 
	 S:=PlusFiniteS;
	 Closed:=false;
      end else
      if E < Minexp then {underflow-set to zero}
      begin S:=ZeroS; Closed:=false;
      end else
      begin cardinality:=finite;exp:=E;mantissa:=M;
      end;
   end else	 
   if M < 0 then
   begin
      while M <= Mininf do
      begin 
	 if M mod 10 < 0 then 
	 begin Closed:=false; M:=M div 10 -1;end
	 else 
	 if M mod 10 = 0 then M:=M div 10 
	 else halt;
	 E:=E+1;
      end;
	 
      while (M>Mininf div 10) do
      begin M:=M*10; E:=E-1; 
      end;

      if E > Maxexp then {overflow}
      begin 
         S:=MinusInfS;
         Closed:=false;
      end 
      else
      if E < Minexp then {underflow}
      begin S:=MinusSmallS; Closed:=false;
      end else
      begin
         cardinality:=finite;exp:=E;mantissa:=M;
      end;
   end;
end;
end;{NormalizeDn}

procedure WriteS(X:Sreal);
var E,M:integer;
begin
with X do
begin
   case edge of
   lin: write('[');
   lout: write('(');
   hin,hout:
   end;
   
   case cardinality of
   infinite: write('inf':Digits+4); 
   finite: 
      if mantissa = 0 then write(0:Digits+1,' ':3)
      else begin
         M:=mantissa;
	 E:=exp; 
         while (M mod 10 = 0) do
	 begin M:=M div 10; E:=E+1;
	 end;
         write(M:Digits+1,'e',E-Digits:2);
      end;
   end;
   
   case edge of 
   hin: write(']');
   hout:write(')');
   lin,lout:
   end;
end;
end;{WriteS}

procedure WriteInt(I:Int);
begin
   with I do begin WriteS(lo); write(','); WriteS(hi); end;
end;{WriteInt}
   
procedure DumpS(X:Sreal);
begin
with X do
   write(edge:4,cardinality:9,mantissa:7,exp:3);
end;{DumpS}

procedure DumpInt(I:Int);
begin
   with I do begin DumpS(lo); write(' || '); DumpS(hi); end;
end;{DumpInt}
   
procedure ReadInt(var I:Int);

var   Ch:char;
      Cll,Clu:boolean;
	
   procedure ReadSUp(var X:Sreal; var Closed:boolean);
   var E,M:integer;
   begin
      with X do
      begin
         deblank;
         case input^ of
         '~':begin X:=PlusInfS;read(Ch);
	     end;
         '-','+','0','1','2','3','4','5','6','7','8','9':
	 begin
	    cardinality:=finite;
   	    read(M);
	    read(E); E:=E+Digits;
	    NormalizeUp(E,M,X,Closed);
	 end;
	 end;{case}
      end;
   end;{ReadSUp}

   procedure ReadSDn(var X:Sreal; var Closed:boolean);
   var E,M:integer;
       Ch:char;
   begin
      with X do
      begin
         deblank;
         case input^ of
         '~':begin X:=MinusInfS;read(Ch);
	     end;
         '-','+','0','1','2','3','4','5','6','7','8','9':
	 begin
	    cardinality:=finite;
   	    read(M);
	    read(E); E:=E+Digits;
	    NormalizeDn(E,M,X,Closed);
	 end;
	 end;{case}
      end;
   end;{ReadSDn}
begin{ReadInt}
   with I do 
   begin 
      deblank; read(Ch); 
      case Ch of
      '[':Cll:=true;
      '(':Cll:=false;
      end;
      ReadSDn(lo,Cll);if Cll then lo.edge:=lin else lo.edge:=lout;
      deblank;
      read(Ch); assert(Ch=',');
      Clu:=true;
      ReadSUp(hi,Clu);
      deblank;
      read(Ch);
      case Ch of
      ']':if Clu then hi.edge:=hin else hi.edge:=hout;
      ')':hi.edge:=hout;
      end;
   end;
end;{ReadInt}
   
procedure DumpTables;
var tL:Loc; tPar:0..Par; tOp:OpType;
begin
	for tOp := print to stop do
	   writeln(tOp:6,ParN[tOp]:2);
	writeln;

	for tL := 1 to End do
	with I[tL] do
	begin
	   write(Code:5);
	   for tPar := 0 to Par do
	      if Pars[tPar] <> 0 then write(Pars[tPar]:4);
	   writeln;
	end;
	writeln('number of memory locations used:',MaxDMem:0);
	writeln;
end;{DumpTables}
	
procedure AlignUp
   (E0:ExpT;M0:ManT;E1:ExpT;M1:ManT;var E,N0,N1:integer;var Closed:boolean);
{Align mantissas M0,M1 preserving accuracy and rounding up wherever possible}
{common resulting exponents in E, and mantissas in N0,N1}
var D:Positive;
begin
   if M0=0 then begin E:=E1;N0:=0;N1:=M1;end else
   if M1=0 then begin E:=E0;N0:=M0;N1:=0;end else
   if E0=E1 then
   begin E:=E0; N0:=M0; N1:=M1;
   end else
   if (E0>E1) then AlignUp(E1,M1,E0,M0,E,N1,N0,Closed) else
   begin
      D:=E1-E0;
      if D>= 2*Digits then
      begin 
         N1:=M1*Maxinf; E:=E1-Digits;
	 if M0<0 then N0:=0 else N0:=1;
	 Closed:=false;
      end else
      if D > Digits then
      begin 
         N1:=M1*Maxinf; E:=E1-Digits; 
	 if (M0 mod Shift[D-Digits]) = 0 
	 then N0:=(M0 div Shift[D-Digits])
	 else
	    if M0 > 0 then N0:=(M0 div Shift[D-Digits])+1
	              else N0:=(M0 div Shift[D-Digits]);
      end else
      {Digits>=D>=0}
      begin N1:=M1*Shift[D]; E:=E1-D; N0:=M0;
      end;
   end;
end;{AlignUp}

function gtS(X,Y:Sreal):boolean;
{X>Y  careful need to be able to compare x] and (x etc.}
var gt:boolean;
begin
   if (X.exp=Y.exp)and(X.mantissa=Y.mantissa) then gt:=X.edge>Y.edge else
   if X.exp = Y.exp then gt:= (X.mantissa > Y.mantissa) else
   if X.mantissa = 0 then gt:= 0 > Y.mantissa else
   if Y.mantissa = 0 then gt:= X.mantissa > 0 else
   if (X.mantissa>0) and (Y.mantissa>0) then gt:= (X.exp > Y.exp) else
   if (X.mantissa>0) and (Y.mantissa<0) then gt:= true else
   if (X.mantissa<0) and (Y.mantissa>0) then gt:= false else
   if (X.mantissa<0) and (Y.mantissa<0) then gt:= (X.exp < Y.exp) 
   else  writeln('error in gtS');
   
   gtS:=gt;
end;{gtS}
   
function geS(X,Y:Sreal):boolean;
{X>=Y  careful need to be able to compare x] and (x etc.}
begin
   if (X.exp=Y.exp)and(X.mantissa=Y.mantissa) then geS:=X.edge>=Y.edge else
   if X.exp = Y.exp then geS:= (X.mantissa >= Y.mantissa) else
   if X.mantissa = 0 then geS:= 0 >= Y.mantissa else
   if Y.mantissa = 0 then geS:= X.mantissa >= 0 else
   if (X.mantissa>0) and (Y.mantissa>0) then geS:= (X.exp > Y.exp) else
   if (X.mantissa>0) and (Y.mantissa<0) then geS:= true else
   if (X.mantissa<0) and (Y.mantissa>0) then geS:= false else
   if (X.mantissa<0) and (Y.mantissa<0) then geS:= (X.exp < Y.exp) 
   else  writeln('error in geS');
end;{geS}
   
function Point(X:Int):boolean;
{X=[x,x]}
begin
with X do
   Point:=(lo.edge=lin)and (hi.edge=hin) and 
	  (lo.mantissa=hi.mantissa) and
	  (lo.exp=hi.exp);
end;{Point}

procedure maxS(X,Y:Sreal;var max:Sreal);
begin
	if gtS(X,Y) then max:=X else max:=Y;
end;

procedure minS(X,Y:Sreal;var min:Sreal);
begin
	if gtS(X,Y) then min:=Y else min:=X;
end;

procedure Inter(P,Q:Int;var R:Int);
begin
   minS(P.hi,Q.hi,R.hi);
   maxS(P.lo,Q.lo,R.lo);
end;

function CheckHi(X:Sreal):boolean;
var OK:boolean;
begin
   OK:=true;
   with X do
   begin
      case cardinality of
      infinite:
         if (exp=Maxexp)and(mantissa=Maxinf) then
	 else writeln('**Invalid hi infinity');
      finite:
      begin
         if (mantissa=Maxinf) or (mantissa=Mininf) then
	 begin OK:=false; writeln('**Invalid finite value - hi');
	 end;
	 
         if mantissa = 0 then
	    if (exp=0) then 
	    else 
	    begin OK:=false; writeln('**Invalid zero - hi')
	    end
	 else
	 begin
	    if (mantissa > 0) then
	       if mantissa >= (Maxinf div 10) then {OK}
	       else 
	       begin OK:=false; writeln('**Incorrect normalization - hi') 
	       end
	    else{mantissa<0}
	       if mantissa > (Mininf div 10) then
	       begin OK:=false; writeln('**Incorrect normalization - hi') 
	       end;
	 end;
      end;
      end;{case}

      if not (edge in [hin,hout]) then
      begin
         OK:=false;
	 writeln('**hi edge value incorrect');
      end;
   end;
   
   CheckHi:=OK;
end;{CheckHi}
  
function CheckLo(X:Sreal):boolean;
var OK:boolean;
begin
   OK:=true;
   with X do
   begin
      case cardinality of
      infinite:
         if (exp=Maxexp)and(mantissa=Mininf) then
	 else writeln('**Invalid lo infinity');
      finite:
      begin
         if (mantissa=Maxinf) or (mantissa=Mininf) then
	 begin OK:=false; writeln('**Invalid finite value - hi');
	 end;
	 
         if mantissa = 0 then
	    if (exp=0) then 
	    else 
	    begin OK:=false; writeln('**Invalid zero - lo')
	    end
	 else
	 begin
	       if (mantissa > 0) then
	          if mantissa >= (Maxinf div 10) then{OK}
		  else 
		  begin OK:=false; writeln('**Incorrect normalization - lo') 
		  end
	       else{mantissa<0}
	          if mantissa > (Mininf div 10) then
		  begin OK:=false; writeln('**Incorrect normalization - lo') 
		  end;
	 end;
      end;
      end;{case}

      if not (edge in [lin,lout]) then
      begin
         OK:=false;
	 writeln('**lo edge value incorrect');
      end;
   end;
   
   CheckLo:=OK;
end;{CheckLo}
  
function CheckInt(I:Int):boolean;
var OK:boolean;
begin
   OK:=CheckHi(I.hi) and CheckLo(I.lo);
   if gtS(I.lo,I.hi) then
   begin
      OK:=false;
      writeln('**Limits out of order');
   end;

   if not OK then 
   begin writeln('**Error in Check'); DumpInt(I);
   end;
   
   CheckInt:=OK;
end;

procedure DumpMem(var DCurr:DataMem);
var tD:Ptr; tL:Loc;
begin
   with DCurr do
   begin
        writeln('LastHalve:',LastHalve:0);
	
    	for tL:= 1 to End do
	   writeln(tL:3,S[tL]:2,RHalve[tL]);
	writeln;
	
	for tD:= 1 to MaxDMem do 
	begin 
	   write(tD:5);
	   DumpInt(D[tD]);
	   assert(CheckInt(D[tD]));
	   writeln;
	end;
	writeln;
   end;
end;{DumpMem}

procedure WriteMem(var DCurr:DataMem);
var tD:Ptr; 
begin
   with DCurr do
   begin
	for tD:= 1 to MaxDMem do 
	if (DF.PF[tD] > PNull) or (Debug > activity) then
	begin 
	   write(tD:5);
	   WriteInt(D[tD]);
	   writeln;
	end;
	writeln;
   end;
end;{WriteMem}

procedure OuterExec
(PC:Loc0;DCurr:DataMem;Change:boolean;First:State;
 var OldCounter:Positive;Level:Positive);

var Counter:Positive;
    Fail,AllPoints,LocalChange:boolean;

procedure NewOuter(F:State);
begin OuterExec(PC,DCurr,Change,F,Counter,Level+1);
end;

{!!}
procedure execprint(PC:Loc; L:Ptr; R0:Int);
begin
   DF.PF[L]:=PSoln;
   writeln;
   write(PC:3,L:5);
   WriteInt(R0);
   writeln;
end;

procedure execpr(var Sr:State; L:Ptr);
begin
   Sr:=-1; DF.PF[L]:=PPrint;
end;{execpr}

procedure exectr(var Sr:State; L:Ptr);
begin
   Sr:=-1; DF.PF[L]:=PTrace;
end;{exectr}

procedure execsoln(var Sr:State; L:Ptr);
begin
   Sr:=-1; DF.PF[L]:=PSoln;
end;{execsoln}

procedure execreadr(var Sr:State;var R0:Int);
begin
   writeln;
   write('<<');
   ReadInt(R0);
   Sr:=-1;
end;

function GetReal(E,M:integer):real;
{convert E-exponent,M-mantissa into genuine Pascal real number}
var x:real;
begin
   x:=M/Maxinf;
   while E>0 do begin x:=x*10; E:=E-1; end; 
   while E<0 do begin x:=x/10; E:=E+1; end;
   GetReal:=x; 
end;{GetReal} 
   
procedure Ratio(Lo,Hi:Sreal;var ERat,MRat:integer); 
{compute ratio of Hi to Lo in exponent mantissa form}
begin
   if Lo.mantissa=0 then
   begin{treat zero as if smallest possible positive number}
      ERat:=Hi.exp-Minexp;
      MRat:=Hi.mantissa*10;
   end else
   if Hi.mantissa=0 then
   begin{treat zero as if smallest possible negative number}
      ERat:=Minexp-Lo.exp;
      MRat:=Lo.mantissa*10;
   end
   else begin
      ERat:=Hi.exp-Lo.exp;
      MRat:=(Hi.mantissa*Maxinf) div Lo.mantissa;
   end;
end;{Ratio}
    
function Adjacent(X:Int):boolean;
{are hi and lo bounds adjacent points}
begin
   with X do
   if (hi.mantissa=0) or (lo.mantissa=0) then
      Adjacent:=
         ((hi.mantissa=0)and(lo.mantissa=Mininf div 10)and(lo.exp=Minexp)) or
         ((lo.mantissa=0)and(hi.mantissa=Maxinf div 10)and(hi.exp=Minexp)) 
   else
      Adjacent:=
         ((lo.exp=hi.exp)and(lo.mantissa+1=hi.mantissa)) or
	 ((hi.exp=lo.exp+1)and(hi.mantissa=(lo.mantissa div 10)+1)) or
	 ((hi.exp=lo.exp-1)and((hi.mantissa div 10)-1=lo.mantissa));
end;{Adjacent}

procedure exechalve
   (var PC:Loc0;var Sr:State;var R0:Int;var OK:boolean;var Change:boolean);
{Reduce range of R0 (suceeds twice for two 'halves')}

var EDiff,MDiff,ERat,MRat,MidE,MidM,M0,M1,HiM,HiE:integer;
    Dummy:boolean;
    Mid:Sreal;
    R,D:real;
    OldPC:Loc;
   
    procedure AtEnd;{What to do afer a successful halve}
    begin  
	DCurr.LastHalve:=PC; PC:=0; Sr:=0;
    end;

begin{exechalve}
OldPC:=PC;
with R0 do
 begin
      if DCurr.LastHalve >= PC then {not our turn yet} else
      if (lo.mantissa = hi.mantissa) and (lo.exp=hi.exp) and
         (lo.edge=lin) and (hi.edge=hin)
      then {single point cant be divided} Sr:=-1 
      else
      if Adjacent(R0) and 
	 (((lo.edge=lout) and (hi.edge=hout)) or
	  ((lo.cardinality=infinite)and(hi.edge=hout)) or 
	  ((hi.cardinality=infinite)and(lo.edge=lout))
	 )
      then Sr:=-1
      else
      if Sr=0 then
         begin
            AlignUp(hi.exp,hi.mantissa,lo.exp,-lo.mantissa,EDiff,M0,M1,Dummy);
            MDiff:=M0+M1;
	    D:=GetReal(EDiff,MDiff);
	    if D < DCurr.RHalve[PC]*HalveThreshold 
	    then {already narrowed enough dont bother} 
	       Change:=true {otherwise can terminate too early}
	    else begin
	       DCurr.RHalve[PC]:=D;
               NewOuter(1);
               NewOuter(2);
	       OK:=false;{fail after both alternatives tried}
	    end
         end
   else{Sr=1,2}
   if Adjacent(R0)
   then begin{two adjacent points - needs special care}
      if (hi.edge=hin) and (hi.cardinality=finite) then
      begin
         case Sr of
	 1:hi.edge:=hout;
	 2:begin lo:=hi; lo.edge:=lin;
	   end;
	 end;

         AtEnd;
      end
      else if (lo.edge=lin) and (lo.cardinality=finite) then 
      begin
         case Sr of
	 1:begin hi:=lo; hi.edge:=hin;
	   end;
	 2:lo.edge:=lout;
	 end;
         AtEnd;
      end else {cant be narrowed} Sr:=-1;

   end{adjacent} else
   begin
      if (lo.mantissa < 0) and (hi.mantissa > 0) then
      begin
         MidM:=0; MidE:=0;
      end else
      begin
         Ratio(lo,hi,ERat,MRat);
         if MRat < 0 then MRat:=-MRat;
         R:=GetReal(ERat,MRat);
(*writeln(ERat,MRat,R);*)
         AlignUp(hi.exp,hi.mantissa,lo.exp,-lo.mantissa,EDiff,M0,M1,Dummy);
         MDiff:=M0+M1;
         if (R > 4) or (R < 0.25) then
         begin{divide hi by sqrt of ratio to get midpoint}
	    if hi.mantissa = 0 
	    then begin HiM:=Mininf div 10; HiE:=Minexp;
	    end else
	    begin HiM:=hi.mantissa; HiE:=hi.exp;
	    end;
	    if ERat < 0 then MidE:=HiE-((ERat-1) div 2)
	 	        else MidE:=HiE-(ERat div 2);
	    if odd(ERat) 
	       then MidM:=trunc(HiM*(Maxinf div 100)/sqrt(MRat*10))
	       else MidM:=trunc(HiM*(Maxinf div 100)/sqrt(MRat));
(*writeln(MidE,MidM);*)
         end else
         begin{take (hi+lo)/2 as midpoint}
            MidM:=MDiff div 2 - M1;
	    MidE:=EDiff;
         end;
      end;
      if MidM >= 0 then NormalizeDn(MidE,MidM,Mid,Dummy)
      		   else NormalizeUp(MidE,MidM,Mid,Dummy);
      case Sr of
      1:begin hi:=Mid; hi.edge:=hout;
        end;
      2:begin lo:=Mid; lo.edge:=lin;
        end;
      end;
(*DumpInt(R0);writeln;*)
      AtEnd;
   end;{if Sr}

   AlignUp(hi.exp,hi.mantissa,lo.exp,-lo.mantissa,EDiff,M0,M1,Dummy);
   MDiff:=M0+M1;
   DCurr.RHalve[OldPC]:=GetReal(EDiff,MDiff);

 end;{with}
end;{exechalve}

procedure exechalves
   (var PC:Loc0;var Sr:State;var R0:Int;var OK:boolean;var Change:boolean);
{Reduce range of R0 (suceeds twice for two 'halves')}
{Simple version thats averages exponents}

var EDiff,MDiff,ERat,MRat,MidE,MidM,M0,M1,HiM,HiE:integer;
    Dummy:boolean;
    Mid:Sreal;
    R,D:real;
    OldPC:Loc;
   
    procedure AtEnd;{What to do afer a successful halve}
    begin  
	DCurr.LastHalve:=PC; PC:=0; Sr:=0;
    end;

    procedure Average(Lo,Hi:Sreal;var Exp:integer);
    {compute average of exponents allowing for zero}
    {infinities happen to work because of representation}
    var Le,He:integer;
    begin
       if Lo.mantissa = 0 then
	  Le := Minexp
       else
	  Le := Lo.exp;
       if Hi.mantissa = 0 then
	  He := Minexp
       else
	  He := Hi.exp;
       Exp:= (He + Le - 2*Minexp) div 2 + Minexp;
writeln(Exp,Hi.exp,Lo.exp,Minexp,He,Le);
    end;{Average}

begin{exechalves}
OldPC:=PC;
with R0 do
 begin
      if DCurr.LastHalve >= PC then {not our turn yet} else
      if (lo.mantissa = hi.mantissa) and (lo.exp=hi.exp) and
         (lo.edge=lin) and (hi.edge=hin)
      then {single point cant be divided} Sr:=-1 
      else
      if Adjacent(R0) and 
	 (((lo.edge=lout) and (hi.edge=hout)) or
	  ((lo.cardinality=infinite)and(hi.edge=hout)) or 
	  ((hi.cardinality=infinite)and(lo.edge=lout))
	 )
      then Sr:=-1
      else
      if Sr=0 then
         begin
            AlignUp(hi.exp,hi.mantissa,lo.exp,-lo.mantissa,EDiff,M0,M1,Dummy);
            MDiff:=M0+M1;
	    D:=GetReal(EDiff,MDiff);
	    if D < DCurr.RHalve[PC]*HalveThreshold 
	    then {already narrowed enough dont bother} 
	       Change:=true {otherwise can terminate too early}
	    else begin
	       DCurr.RHalve[PC]:=D;
               NewOuter(1);
               NewOuter(2);
	       OK:=false;{fail after both alternatives tried}
	    end
         end
   else{Sr=1,2}
   if Adjacent(R0)
   then begin{two adjacent points - needs special care}
      if (hi.edge=hin) and (hi.cardinality=finite) then
      begin
         case Sr of
	 1:hi.edge:=hout;
	 2:begin lo:=hi; lo.edge:=lin;
	   end;
	 end;

         AtEnd;
      end
      else if (lo.edge=lin) and (lo.cardinality=finite) then 
      begin
         case Sr of
	 1:begin hi:=lo; hi.edge:=hin;
	   end;
	 2:lo.edge:=lout;
	 end;
         AtEnd;
      end else {cant be narrowed} Sr:=-1;

   end{adjacent} else
   begin
      if (lo.mantissa < 0) and (hi.mantissa > 0) then
      begin
         MidM:=0; MidE:=0;
      end else
      begin
         Ratio(lo,hi,ERat,MRat);
(*writeln(ERat,MRat,R);*)
         AlignUp(hi.exp,hi.mantissa,lo.exp,-lo.mantissa,EDiff,M0,M1,Dummy);
         MDiff:=M0+M1;
         if (ERat > 1) or (ERat < -1) then
         begin{Average exponents}
	    if hi.mantissa <= 0 
	    then begin MidM:= -Splitman;
	    end else
	    begin MidM:= Splitman; assert(lo.mantissa >= 0);
	    end;
            Average(lo,hi,MidE);
         end else
         begin{take (hi+lo)/2 as midpoint}
            MidM:=MDiff div 2 - M1;
	    MidE:=EDiff;
         end;
      end;
      if MidM >= 0 then NormalizeDn(MidE,MidM,Mid,Dummy)
      		   else NormalizeUp(MidE,MidM,Mid,Dummy);
      case Sr of
      1:begin lo:=Mid; lo.edge:=lin;
        end;
      2:begin hi:=Mid; hi.edge:=hout;
        end;
      end;
(*DumpInt(R0);writeln;*)
      AtEnd;
   end;{if Sr}

   AlignUp(hi.exp,hi.mantissa,lo.exp,-lo.mantissa,EDiff,M0,M1,Dummy);
   MDiff:=M0+M1;
   DCurr.RHalve[OldPC]:=GetReal(EDiff,MDiff);

 end;{with}
end;{exechalves}

procedure execlinh
   (var PC:Loc0;var Sr:State;var R0:Int;var OK:boolean;var Change:boolean);
{Reduce range of R0 (suceeds twice for two 'halves')}

var EDiff,MDiff,MidE,MidM,M0,M1:integer;
    Dummy:boolean;
    Mid:Sreal;
    D:real;
    OldPC:Loc;
   
    procedure AtEnd;{What to do afer a successful halve}
    begin  
	DCurr.LastHalve:=PC; PC:=0; Sr:=0;
    end;

begin{execlinh}
OldPC:=PC;
with R0 do
 begin
      if DCurr.LastHalve >= PC then {not our turn yet} else
      if (lo.mantissa = hi.mantissa) and (lo.exp=hi.exp) and
         (lo.edge=lin) and (hi.edge=hin)
      then {single point cant be divided} Sr:=-1 
      else
      if Adjacent(R0) and 
	 (((lo.edge=lout) and (hi.edge=hout)) or
	  ((lo.cardinality=infinite)and(hi.edge=hout)) or 
	  ((hi.cardinality=infinite)and(lo.edge=lout))
	 )
      then Sr:=-1
      else
      if Sr=0 then
         begin
            AlignUp(hi.exp,hi.mantissa,lo.exp,-lo.mantissa,EDiff,M0,M1,Dummy);
            MDiff:=M0+M1;
	    D:=GetReal(EDiff,MDiff);
	    if D < DCurr.RHalve[PC]*HalveThreshold
	    then {already narrowed enough dont bother} 
	       Change:=true {otherwise possible to terminate early}
	    else begin
	       DCurr.RHalve[PC]:=D;
               NewOuter(1);
               NewOuter(2);
	       OK:=false;{fail after both alternatives tried}
	    end
         end
   else{Sr=1,2}
   if Adjacent(R0)
   then begin{two adjacent points - needs special care}
      if (hi.edge=hin) and (hi.cardinality=finite) then
      begin
         case Sr of
	 1:begin lo:=hi; lo.edge:=lin;
	   end;
	 2:hi.edge:=hout;
	 end;
         AtEnd;
      end
      else if (lo.edge=lin) and (lo.cardinality=finite) then 
      begin
         case Sr of
	 1:lo.edge:=lout;
	 2:begin hi:=lo; hi.edge:=hin;
	   end;
	 end;
         AtEnd;
      end else {cant be narrowed} Sr:=-1;
   end{adjacent} else
   begin
      if (lo.mantissa < 0) and (hi.mantissa > 0) then
      begin
         MidM:=0; MidE:=0;
      end else
      begin
         AlignUp(hi.exp,hi.mantissa,lo.exp,-lo.mantissa,EDiff,M0,M1,Dummy);
         MDiff:=M0+M1;
         MidM:=MDiff div 2 - M1;
	 MidE:=EDiff;
      end;
      if MidM >= 0 then NormalizeDn(MidE,MidM,Mid,Dummy)
      		   else NormalizeUp(MidE,MidM,Mid,Dummy);
      case Sr of
      1:begin lo:=Mid; lo.edge:=lin;
        end;
      2:begin hi:=Mid; hi.edge:=hout;
        end;
      end;
      
      AtEnd;
   end;{if Sr}

   AlignUp(hi.exp,hi.mantissa,lo.exp,-lo.mantissa,EDiff,M0,M1,Dummy);
   MDiff:=M0+M1;
   DCurr.RHalve[OldPC]:=GetReal(EDiff,MDiff);

 end;{with}
end;{execlinh}

procedure execmult(var Sr:State;T0,T1,T2:Int;var R0,R1,R2:Int;var OK:boolean);
var Q0,Q1,Q2:Int;

   procedure multS(S0,S1:Sreal;var U,D:Sreal);
   var M,E:integer;
       Closed,Clu,Cld:boolean;
   begin
      M:=S0.mantissa*S1.mantissa;
(*DumpS(S0);write('//');DumpS(S1);write(M);*)
      Closed:=(S0.edge in [hin,lin]) and (S1.edge in [hin,lin]);
      if ((S0.mantissa=0) and (S0.edge in [hin,lin])) or
         ((S1.mantissa=0) and (S1.edge in [hin,lin]))
      then Closed:=true; 
      Clu:=Closed; Cld:=Closed;
      if (S0.cardinality=infinite) or (S1.cardinality=infinite) then
      begin
         if M < 0 then begin D:=MinusInfS; U:=MinusInfS; end else
	 if M > 0 then begin D:=PlusInfS; U:=PlusInfS; end else
	 begin {M=0} D:=ZeroS; U:=ZeroS; end;
	 Closed:=((S0.cardinality=infinite)and(S0.edge in [hin,lin]))or
	         ((S1.cardinality=infinite)and(S1.edge in [hin,lin]));
	 Clu:=Closed;Cld:=Closed;
      end
      else{everybody finite}
      begin
         E:=S0.exp+S1.exp-Digits;
	 NormalizeUp(E,M,U,Clu);
	 NormalizeDn(E,M,D,Cld);
      end;
      if Clu then U.edge:=hin else U.edge:=hout;
      if Cld then D.edge:=lin else D.edge:=lout;      
(*writeln(E);DumpS(U);write('::');DumpS(D);writeln;*)
   end;{multS}
         
   procedure mult(Ta,Tb:Int;var R:Int);
   var U0,U1,U2,U3,U4,U5,D0,D1,D2,D3,D4,D5:Sreal;
   begin
      multS(Ta.hi,Tb.hi,U0,D0);
      multS(Ta.hi,Tb.lo,U1,D1);
      multS(Ta.lo,Tb.hi,U2,D2);
      multS(Ta.lo,Tb.lo,U3,D3);
      maxS(U0,U1,U4);maxS(U2,U3,U5);maxS(U4,U5,R.hi);
      minS(D0,D1,D4);minS(D2,D3,D5);minS(D4,D5,R.lo);
   end;
   
   procedure InvS(S:Sreal;var W:Sreal);
   var E,M,Rem:integer;
       Closed:boolean;
   begin
      Closed:= S.edge in [hin,lin];
      if (S.cardinality = infinite) then
         W:=ZeroS
      else
      if (S.mantissa = 0) then
         case S.edge of
	 hin,hout:W:=MinusInfS;
	 lin,lout:W:=PlusInfS;
	 end
      else
      begin
         M:=(Maxinf*Maxinf) div S.mantissa;
	 Rem:=(Maxinf*Maxinf) mod S.mantissa;
	 if Rem < 0 then halt;
	 E:=-S.exp;
	 case S.edge of
	 lin,lout: begin 
	     	      if (Rem > 0) and (M > 0) then 
		      begin M:=M+1;Closed:=false; 
		      end;
		      NormalizeUp(E,M,W,Closed);
	           end;
	 hin,hout: begin 
	     	      if (Rem > 0) and (M < 0) then 
		      begin M:=M-1;Closed:=false;
		      end;
		      NormalizeDn(E,M,W,Closed);
	           end;
	 end;
      end;
      
      if Closed then
         case S.edge of
         hin:W.edge:=lin;
         lin:W.edge:=hin;
         end
      else
         case S.edge of
	 hin,hout:W.edge:=lout;
	 lin,lout:W.edge:=hout;
	 end;

      
   end;{InvS}	 
   
   procedure Inv(T:Int;var X:Int;Pos:boolean);
   {1/T positive -> X}
   {If 1/T splits to two intervals then use Pos to select which to use}
   begin
      if (T.lo.mantissa < 0) and (T.hi.mantissa > 0) then
         if (T.lo.cardinality=infinite) and (T.hi.cardinality=infinite) then
	    X:=All
	 else if Pos then
	 begin InvS(T.hi,X.lo); X.hi:=PlusInfS; X.hi.edge:=hin;
	 end else 
	 begin InvS(T.lo,X.hi); X.lo:=MinusInfS; X.lo.edge:=lin;
	 end
      else
      begin InvS(T.hi,X.lo); InvS(T.lo,X.hi);
      end;
   end;{Inv}
   
   procedure divi(Ta,Tb:Int;var R:Int);
   var X:Int;
   begin
      if (Tb.lo.mantissa < 0) and (Tb.hi.mantissa > 0) then
         if (Ta.lo.mantissa < 0) and (Ta.hi.mantissa > 0) then
	 { need do nothing as R will be set to [inf,inf]}
	 else
	 
         begin
	    {if both same sign get positive side of inverse}
	    {else get negative}
	    Inv(Tb,X,(Ta.hi.mantissa <= 0) = (R.hi.mantissa <= 0));
	    mult(Ta,X,R);
	 end
      else {Tb wont give split inverse}
      begin
         Inv(Tb,X,true);
	 mult(Ta,X,R);
      end;
(*
DumpInt(Tb);writeln('//');DumpInt(X);writeln;
DumpInt(Ta);writeln('\\');DumpInt(R);writeln;
*)
   end;
   
   function Split(T:Int):boolean;
   begin
      Split:=(T.lo.mantissa<0) and (T.hi.mantissa>0) 
      	      and ((T.lo.cardinality=finite) or (T.hi.cardinality=finite));
   end;{Split}

   function Zin(T:Int):boolean;
   {check if 0 in range of interval}
   begin
      if (T.lo.mantissa > 0) then Zin:=false else
      if (T.lo.mantissa = 0) then
	 Zin:=(T.lo.edge=lin) else
      if (T.hi.mantissa < 0) then Zin:=false else
      if (T.hi.mantissa = 0) then
         Zin:=(T.hi.edge=hin) 
      else
         Zin:=true;
   end;{Zin}
   
begin{execmult}
   case Sr of
   0,10:begin
        if T2=Zero then
           if (T1=Zero) or (T0=Zero) then Sr:=-1
           else
	   if not Zin(T0) then begin R1:=Zero; Sr:=-1; end else
	   if not Zin(T1) then begin R0:=Zero; Sr:=-1; end 
	   else
           begin
              NewOuter(11); NewOuter(12);OK:=false;     
           end
        else if (Sr=0) then
        begin
	   if (T0.hi.mantissa > 0) and (T0.lo.mantissa < 0) and Split(T1) 
           then  begin NewOuter(1); NewOuter(2); OK:=false; end
           else if (T1.hi.mantissa > 0) and 
	           (T1.lo.mantissa < 0) and Split(T0) 
                then  begin NewOuter(3); NewOuter(4); OK:=false; end;
	end;
     end;
   1:begin R0.lo:=ZeroS; R0.lo.edge:=lin; T0:=R0; Sr:=10;
     end;
   2:begin R0.hi:=ZeroS; R0.hi.edge:=hout; T0:=R0; Sr:=10;
     end;
   3:begin R1.lo:=ZeroS; R1.lo.edge:=lin; T1:=R1; Sr:=10;
     end;
   4:begin R1.hi:=ZeroS; R1.hi.edge:=hout; T1:=R1; Sr:=10;
     end;
   11:begin R0:=Zero; Sr:=-1;
      end;
   12:begin R1:=Zero; Sr:=-1;
      end;
   end;
   
   if OK and (Sr<>-1) then
   begin
      mult(T0,T1,Q2); Inter(R2,Q2,R2);
      Q1:=R1; divi(T2,T0,Q1); Inter(R1,Q1,R1);
      Q0:=R0; divi(T2,T1,Q0); Inter(R0,Q0,R0);
      Sr:=10;
   end;
end;{execmult}

procedure execadd(T0,T1,T2:Int;var R0,R1,R2:Int);
  procedure addhi(S0,S1:Sreal; var S2:Sreal);
  var Closed:boolean;  Exp,M0,M1:integer;
  begin{addhi}
  with S2 do
  begin
     if (S0.cardinality=infinite)or(S1.cardinality=infinite) then
     begin  
        S2:=PlusInfS;
        Closed:=((S0.cardinality=infinite)and(S0.edge=hin))or
	        ((S1.cardinality=infinite)and(S1.edge=hin));
     end else
     begin
        Closed:=(S0.edge=hin)and(S1.edge=hin);
        AlignUp(S0.exp,S0.mantissa,S1.exp,S1.mantissa,Exp,M0,M1,Closed);
	NormalizeUp(Exp,M0+M1,S2,Closed)
     end;
     if Closed then S2.edge:=hin else S2.edge:=hout;
  end;
  end;{addhi}
  
  procedure addlo(S0,S1:Sreal; var S2:Sreal);
  var Closed:boolean;  Exp,M0,M1:integer;
  begin{addlo}
  with S2 do
  begin
     if (S0.cardinality=infinite)or(S1.cardinality=infinite) then
     begin  
        S2:=MinusInfS;
        Closed:=((S0.cardinality=infinite)and(S0.edge=lin))or
	        ((S1.cardinality=infinite)and(S1.edge=lin));
     end else
     begin
        Closed:=(S0.edge=lin)and(S1.edge=lin);
        AlignUp(S0.exp,-S0.mantissa,S1.exp,-S1.mantissa,Exp,M0,M1,Closed);
	NormalizeUp(Exp,M0+M1,S2,Closed); mantissa:=-mantissa;
     end;
     if Closed then S2.edge:=lin else S2.edge:=lout;
  end;
  end;{addlo}
  
  procedure subhi(S0,S1:Sreal; var S2:Sreal);
  var Closed:boolean;  Exp,M0,M1:integer;
  begin{subhi}
  with S2 do
  begin
     if (S0.cardinality=infinite)or(S1.cardinality=infinite) then
     begin  
        S2:=PlusInfS;
        Closed:=((S0.cardinality=infinite)and(S0.edge=hin))or
	        ((S1.cardinality=infinite)and(S1.edge=lin));
     end else
     begin
        Closed:=(S0.edge=hin)and(S1.edge=lin);
        AlignUp(S0.exp,S0.mantissa,S1.exp,-S1.mantissa,Exp,M0,M1,Closed);
	NormalizeUp(Exp,M0+M1,S2,Closed);
     end;
     if Closed then S2.edge:=hin else S2.edge:=hout;
  end;
  end;{subhi}
  
  procedure sublo(S0,S1:Sreal; var S2:Sreal);
  var Closed:boolean;  Exp,M0,M1:integer;
  begin{sublo}
  with S2 do
  begin
     if (S0.cardinality=infinite)or(S1.cardinality=infinite) then
     begin  
        S2:=MinusInfS;
        Closed:=((S0.cardinality=infinite)and(S0.edge=lin))or
	        ((S1.cardinality=infinite)and(S1.edge=hin));
     end else
     begin
        Closed:=(S0.edge=lin)and(S1.edge=hin);
        AlignUp(S0.exp,-S0.mantissa,S1.exp,S1.mantissa,Exp,M0,M1,Closed);
	NormalizeUp(Exp,M0+M1,S2,Closed);mantissa:=-mantissa;
     end;
     if Closed then S2.edge:=lin else S2.edge:=lout;
  end;
  end;{sublo}
  
begin{execadd}
   addhi(T0.hi,T1.hi,R2.hi);
   addlo(T0.lo,T1.lo,R2.lo);
   
   subhi(T2.hi,T0.lo,R1.hi);
   sublo(T2.lo,T0.hi,R1.lo);
   
   subhi(T2.hi,T1.lo,R0.hi);
   sublo(T2.lo,T1.hi,R0.lo);
end;{execadd}




procedure execintgr(var Sr:State; var R:Int);
      
  procedure floor (var R : Sreal);
  var sign , dum : boolean ;
      E, M ,t    : integer ;
  
  begin
     sign := false ;
     with R do
        begin
           if (mantissa < 0) then
              begin
                 sign := true ;
                 mantissa := - mantissa ;
              end ;
           if (exp <= 0) then
              begin
                 if sign or ((mantissa = 0) & (edge = hout)) then
                    begin
                       M := 1 ; 
                       sign := true ;
                    end 
                 else
                    M := 0 ;
                 E := Digits ;
                 NormalizeUp (E,M,R,dum) ;
                 edge := hin ;
              end 
        
           else {exp >0}
              if (exp <= Digits) then
                 begin
                    M := 1 ;
                    E := exp ;
                    while (E < Digits) do
                       begin
                          M := M * 10 ;
                          E := E + 1 ;
                       end ;
                    t := mantissa mod M ;
                    M := mantissa div M ;
                    if (sign & ((edge = hout) or(t > 0))) then
                       M := M + 1 ; 
                    if (not sign & (t = 0)) & (edge = hout) then
                       M := M - 1 ;
                    E := Digits ;
                    NormalizeUp (E,M,R,dum) ;
                    edge := hin ;
                 end 
              else
                 if ((edge = hout)&(exp = (Digits+1))) & (not sign & (mantissa = Splitman)) then
                    begin
                       mantissa := Maxman ;
                       exp := Digits ;
                       edge := hin ;
                    end ;
           if sign then
              mantissa := - mantissa ;
        end ;{with R}
  end ; {floor} 
  procedure ceiling (var R : Sreal);
  var sign , dum : boolean ;
      E, M , t   : integer ;
  
  begin
     sign := false ;
     with R do
        begin
           if (mantissa < 0) then
              begin
                 sign := true ;
                 mantissa := - mantissa ;
              end ;
           if (exp <= 0) then
              begin
                 if sign or ((mantissa = 0) & (edge = lin)) then
                    M := 0 
                 else
                    M := 1 ;
                 E := Digits ;
                 NormalizeDn (E,M,R,dum) ;
                 edge := lin ;
              end 
        
           else {exp > 0}
              if (exp <= Digits) then
                 begin
                    M := 1 ;
                    E := exp ;
                    while (E < Digits) do
                       begin
                          M := M * 10 ;
                          E := E + 1 ;
                       end ;
                    t := mantissa mod M ;
                    M := mantissa div M ;
                    if ( not sign & ((edge = lout) or(t > 0))) then
                       M := M + 1 ;
                    if (sign & (t = 0)) & (edge = lout) then
                       M := M - 1 ;
                    E := Digits ;
                    NormalizeDn (E,M,R,dum) ;
                    edge := lin ;
                 end 
              else
                 if ((edge = lout)&(exp = (Digits+1))) & (sign & (mantissa = Splitman)) then
                    begin
                       mantissa := Maxman ;
                       exp := Digits ;
                       edge := lin ;
                    end ;
           if sign then
              mantissa := - mantissa ;
        end ;{with R}
  end ; {ceiling} 
begin
   with R do
      begin
(*         writeln ('IN EXECINTGR :') ;
         writeln ;
         writeln ('HI : ', hi.mantissa , hi.exp) ;
         writeln ;
         writeln ('LO : ', lo.mantissa , lo.exp) ;
         writeln ;
*)
         if (hi.cardinality <> infinite) then
            floor (hi) ;
         if (lo.cardinality <> infinite) then
            ceiling (lo) ;
         if ((hi.mantissa = lo.mantissa) & (hi.exp = lo.exp)) then
            Sr := - 1 ;
(*         writeln ('OUT EXECINTGR :') ;
         writeln ;
         writeln ('HI : ', hi.mantissa , hi.exp) ;
         writeln ;
         writeln ('LO : ', lo.mantissa , lo.exp) ;
         writeln ;
*)
      end ;
end;{execintgr}













  procedure execlb (R1 : Int ; var R : Int) ;
  begin
     R := R1 ;
     with R.lo do
        if (cardinality = infinite) then
           R.hi := MinusFiniteS 
        else
           R.hi := R.lo ;
     R.hi.edge := hin ;
     R.lo := MinusInfS ;
  end ;

procedure execub (var X , Xd : Int) ;
var     Dum : Int ;     
  begin
     Xd := X ;
     execadd (Xd, Dum, Zero, Dum, Xd, Dum) ;
     execlb (Xd,Xd) ;
     execadd (Xd, Dum, Zero, Dum, Xd, Dum) ;
  end ;

procedure execcopy (R0 :Int; var R1:Int);
begin
  R1:=R0;
end;

procedure execless(var Sr:State; var R0,R1:Int);
{R0 < R1}
begin
   if Point(R0) or Point(R1) then Sr:=-1;
   if gtS(R1.lo,R0.hi) then Sr:= -1 else
   begin
      R0.hi:=R1.hi;
      R0.hi.edge:=hout;
      R1.lo:=R0.lo;
      R1.lo.edge:=lout;
   end;
end;{execless}

procedure execleq(var Sr:State; var R0,R1:Int);
{R0 =< R1}
begin
   if Point(R0) or Point(R1) then Sr:=-1;
   if geS(R1.lo,R0.hi) then Sr:= -1 else
   begin
      R0.hi:=R1.hi;
      R1.lo:=R0.lo;
   end;
end;{execleq}

procedure execnoteq(var Sr:State; var R0,R1:Int);
{R0 <> R1}
begin
   case Sr of
   0:{nothing done yet}
     begin
     if gtS(R0.lo,R1.hi) or gtS(R1.lo,R0.hi) 
     then Sr:=-1 {no need to check in future}
     else 
     begin
        if Point(R0) then 
	begin
	   OuterExec(PC,DCurr,true,1,Counter,Level+1);
	   Sr:=2;
	   execless(Sr,R1,R0);
	end else
	if Point(R1) then
	begin
	   OuterExec(PC,DCurr,true,2,Counter,Level+1);
	   Sr:=1;
	   execless(Sr,R0,R1);
	end;
     end;
     end;
   1:execless(Sr,R0,R1);
   2:execless(Sr,R1,R0);
   end;
end;{execnoteq}

procedure execsqrr(var R0,R1:Int);
begin{execsqrr}
end;{execsqrr}

procedure execminr(var R0,R1,R2:Int);
begin{execminr}
end;{execminr}

procedure execmaxr(var R0,R1,R2:Int);
  procedure chmaxhi(S0,S1:Sreal; var S2:Sreal);
  var Closed:boolean;  Exp,M0,M1:integer;
  begin{chmaxhi}
  with S2 do
  begin
     if (S0.cardinality=infinite)or(S1.cardinality=infinite) then
     begin  
        S2:=PlusInfS;
        Closed:=((S0.cardinality=infinite)and(S0.edge=hin))or
	        ((S1.cardinality=infinite)and(S1.edge=hin));
     end else
     begin
        Closed:=(S0.edge=hin)and(S1.edge=hin);
        AlignUp(S0.exp,S0.mantissa,S1.exp,S1.mantissa,Exp,M0,M1,Closed);
        if M1 > M0 then
           M0 := M1 ;
	NormalizeUp(Exp,M0,S2,Closed)
     end;
     if Closed then S2.edge:=hin else S2.edge:=hout;
  end;
  end;{chmaxhi}
  
  procedure chmaxlo(S0,S1:Sreal; var S2:Sreal);
  var Closed:boolean;  Exp,M0,M1:integer;
  begin{chmaxlo}
  with S2 do
  begin
     if (S0.cardinality=infinite)or(S1.cardinality=infinite) then
     begin  
        S2:=MinusInfS;
        Closed:=((S0.cardinality=infinite)and(S0.edge=lin))or
	        ((S1.cardinality=infinite)and(S1.edge=lin));
     end else
     begin
        Closed:=(S0.edge=lin)and(S1.edge=lin);
        AlignUp(S0.exp,-S0.mantissa,S1.exp,-S1.mantissa,Exp,M0,M1,Closed);
	NormalizeUp(Exp,M0+M1,S2,Closed); mantissa:=-mantissa;
     end;
     if Closed then S2.edge:=lin else S2.edge:=lout;
  end;
  end;{addlo}
begin{execmaxr}
end;{execmaxr}

procedure execmodu(var R0,R1,R2:Int);
begin{execmodu}
end;{execmodu}

procedure execabsr(var R0,R1:Int);
begin{execabsr}
end;{execabsr}

procedure exectrig(var R0,R1,R2:Int);
begin{exectrig}
end;{exectrig}

procedure execexpr(var R0,R1:Int);
begin{execexpr}
end;{execexpr}


function Exec(I:Instr;var PC:Loc0;var Change:boolean):boolean;
var
	R:array[0..Par] of Int;  {working registers}
	Sr:State;  {State register}
	P:0..Par;
	E:boolean;
	NewPC:Loc0;
	TraceChange:boolean;

   procedure WritePars; {write out list of parameter registers for curr ins}
   begin
   with I do
   begin
      write(PC:2,Code:5,Sr:3);
      for P := 0 to Par do
         if Pars[P] <> 0 then 
	 begin
	    write(Pars[P]:3);
	    WriteInt(R[P]);
	 end;
      writeln;
   end;
   end;{WritePars}

begin{Exec}
with I,DCurr do
begin
   Counter:=Counter+1;
   {get parameters}
   for P := 0 to ParN[Code] do 
   begin R[P]:=D[Pars[P]]; assert(CheckInt(R[P]));
   end;
   
   Sr:=S[PC];
   if Debug >= trace then  begin write(' '); WritePars; end;
   E:=true;
   Change:=false;
   NewPC:=PC;

{!!}case Code of 
   print: execprint(PC,Pars[0],R[0]);
   pr   : execpr(Sr,Pars[0]);
   tr   : exectr(Sr,Pars[0]);
   soln : execsoln(Sr,Pars[0]);
   readr: execreadr(Sr,R[0]);
   halve: exechalve(NewPC,Sr,R[0],E,Change);
   halves:exechalves(NewPC,Sr,R[0],E,Change);
   linh : execlinh(NewPC,Sr,R[0],E,Change);
   mult : execmult (Sr,R[0],R[1],R[2],R[0],R[1],R[2],E);
   add  : execadd  (R[0],R[1],R[2],R[0],R[1],R[2]);
   intgr: execintgr(Sr,R[0]);
   less : execless (Sr,R[0],R[1]);
   leq  : execleq  (Sr,R[0],R[1]);
   noteq: execnoteq(Sr,R[0],R[1]);
   sqrr : execsqrr(R[0],R[1]);
   minr : execminr(R[0],R[1],R[2]);
   maxr : execmaxr(R[0],R[1],R[2]);
   modu : execmodu(R[0],R[1],R[2]);
   absr : execabsr(R[0],R[1]);
   trig : exectrig(R[0],R[1],R[2]);
   expr : execexpr(R[0],R[1]);
   lb   : execlb (R[0],R[1]);
   ub   : execub (R[0],R[1]);
   copy : execcopy(R[0],R[1]);
   end;

   TraceChange:=false;
   AllPoints:=true;
   for P := 0 to ParN[Code] do
   with D[Pars[P]] do
   begin
      if DF.PF[Pars[P]]=PPrint then TraceChange:=true;
      assert(CheckLo(R[P].lo));assert(CheckHi(R[P].hi));
      if ParIntersect [Code] then
         begin
            maxS(R[P].lo,lo,R[P].lo);
            minS(R[P].hi,hi,R[P].hi);
         end ;
      if gtS(R[P].lo,R[P].hi) then 
      begin E:=false; assert(CheckLo(R[P].lo));assert(CheckHi(R[P].hi));
      end
      else begin
         if D[Pars[P]] <> R[P] then 
         begin 
	    D[Pars[P]] := R[P]; 
	    Change:=true;
	    if DF.PF[Pars[P]] = PTrace then TraceChange:=true;
         end;
         AllPoints:=AllPoints and Point(R[P]);
         assert(CheckInt(R[P])); assert(CheckInt(D[Pars[P]]));
      end;
   end;

   if (Debug=activity) and TraceChange then writeln;
   if (Debug >=activity) then 
   begin if Change then write('*') else write ('.');
   end;
   Exec:=E;
   if E then
   begin
      if AllPoints then Sr:=-1; 
      if (Sr <> S[PC]) then begin S[PC]:=Sr; Change:=true; end;
      if (Debug=activity) and TraceChange then WritePars;
      if Debug >= post then  WritePars;
      if Debug = dump then DumpMem(DCurr);
   end else 
   if Debug >= activity then 
   begin writeln('FAILED'); write(' '); WritePars; 
   end;
   PC:=NewPC;
end;
end;{Exec}

begin{OuterExec}
   writeln;
   writeln(Level:2,'Entering  Count:',OldCounter:0); OldCounter:=0;
   Counter:=0;
   Fail:=false;
   if First <> 0 then DCurr.S[PC]:=First;
   {Run simulation until failure or nothing further to be done}
   repeat
        if (PC = End) then 
   	begin PC:=1; Change:=false; DCurr.LastHalve:=1; end;
   	while (PC < End) and not Fail and not GlobalEnd do
   	with I[PC] do
   	begin
   	   if DCurr.S[PC] > -1 then
	   begin Fail:=not Exec(I[PC],PC,LocalChange); 
	      Change:=Change or LocalChange;
	   end;
   	   PC:=PC+1;
   	end;
   until Fail or (not Change) or GlobalEnd;
   writeln;
   write(Level:2,'Exiting  Count:',Counter:0);
   if not (Fail or GlobalEnd) then
   begin 
      if (Cut=once) then GlobalEnd:=true;
      writeln('SOLUTION');
      WriteMem(DCurr);
   end 
   else writeln;
end;{OuterExec}


procedure Clear;
var tL:Loc; 
    tD,tDF:Ptr; 
    tPar:1..Par; 
    DI:1..Digits;
    J:1..Maxexp;
    MaxDiff:real;
begin
   Shift[0]:=1;
   for DI:= 1 to Digits do Shift[DI]:=Shift[DI-1]*10;

   with PlusInfS do
   begin
      edge:=hin;cardinality:=infinite;mantissa:=Maxinf;
      exp:=Maxexp;
   end;
   with MinusInfS do
   begin
      edge:=lin;cardinality:=infinite;mantissa:=Mininf;
      exp:=Maxexp;
   end;
   with PlusFiniteS do
   begin
      edge:=hin;cardinality:=finite;mantissa:=Maxman;
      exp:=Maxexp;
   end;
   with MinusFiniteS do
   begin
      edge:=lin;cardinality:=finite;mantissa:=Minman;
      exp:=Maxexp;
   end;
   with ZeroS do
   begin exp:=0;mantissa:=0;edge:=hin;cardinality:=finite;
   end;
   with PlusSmallS do
   begin exp:=Minexp;mantissa:=Maxinf div 10; cardinality:=finite;
   end;
   with MinusSmallS do
   begin exp:=Minexp;mantissa:=Mininf div 10; cardinality:=finite;
   end;
   


   with Zero do
   begin lo:=ZeroS;lo.edge:=lin; hi:=ZeroS;hi.edge:=hin;
   end;
   with All do
   begin hi:=PlusInfS; lo:=MinusInfS;
   end;
   with AllFinite do
   begin lo:=MinusFiniteS; hi:=PlusFiniteS;
   end;

   with DF do
   begin
        for tDF:= 1 to DMem do PF[tDF]:=PNull;
   end;
  
   with DInit do
   begin
	for tD:= 1 to DMem do
	   if Verifiable then D[tD]:=AllFinite
	   		 else D[tD]:=All;
	LastHalve:=1;

	MaxDiff:=2;
	for J:=1 to Maxexp do MaxDiff:=MaxDiff*10;
	
	for tL := 1 to IMem do
	begin
	   RHalve[tL]:=MaxDiff;
	   S[tL]:=0;
	   with I[tL] do
	   for tPar := 1 to Par do
		Pars[tPar]:=0;
	end;

{!!}	ParN[print]:=0;
        ParN[pr]:=0;
        ParN[tr]:=0;
        ParN[soln]:=0;
	ParN[halve]:=0;
	ParN[halves]:=0;
	ParN[readr]:=0;
	ParN[linh]:=0;
	ParN[mult]:=2;
	ParN[add]:=2;
	ParN[intgr]:= 0;
	ParN[less]:= 1;
	ParN[leq]:= 1;
	ParN[noteq]:= 1;
	ParN[sqrr]:= 1;
	ParN[minr]:=2;
	ParN[maxr]:=2;
	ParN[modu]:= 1;
	ParN[absr]:= 1;
	ParN[trig]:=2;
	ParN[expr]:= 1;
        ParN[lb]:= 1;
        ParN[ub]:= 1; 
        ParN[copy]:= 1; 
	ParN[stop]:=-1;
{!!}	ParIntersect[print]:= true;
        ParIntersect[pr]:= true;
        ParIntersect[tr]:= true;
        ParIntersect[soln]:= true;
	ParIntersect[halve]:=true;
	ParIntersect[halves]:=true;
	ParIntersect[readr]:=true;
	ParIntersect[linh]:=true;
	ParIntersect[mult]:=true;
	ParIntersect[add]:=true;
	ParIntersect[intgr]:= true;
	ParIntersect[less]:= true;
	ParIntersect[leq]:= true;
	ParIntersect[noteq]:= true;
	ParIntersect[sqrr]:= true;
	ParIntersect[minr]:= true;
	ParIntersect[maxr]:= true;
	ParIntersect[modu]:= true;
	ParIntersect[absr]:= true;
	ParIntersect[trig]:= true;
	ParIntersect[expr]:= true;
        ParIntersect[lb]:= false;
        ParIntersect[ub]:= false;
	ParIntersect[stop]:= true;
	ParIntersect[copy]:= true;
   end;
end;{Clear}
	
procedure ReadInstr;
var
	tP:0..Par;
	Op:OpType;
	tDat:Ptr;
begin
   with DInit do
   begin
	End:=1;
	MaxDMem:=0;
	repeat
	   with I[End] do
	   begin
	      read(Op);
	      Code:=Op;
	      for tP := 0 to ParN[Op] do with I[End] do 
	      begin
	         read(tDat); Pars[tP]:=tDat;
		 if tDat>MaxDMem then MaxDMem:=tDat;
		 if MaxDMem > DMem then 
		 begin writeln('Too many variables');halt;
		 end;
	      end;
	      readln;
	   end;
	   End:=End+1; 
	   if End >= IMem then begin writeln('Too many instructions');halt;end;
	until Op = stop;
	End:=End-1;

	while not eof do {read constant values for memory locations}
	begin

   	   read(tDat);
	   if tDat > DMem then writeln('Variable out of range',tDat,DMem);
	   ReadInt(D[tDat]);
	   readln;
	end;
   end;
end;{ReadInstr}

begin
	GlobalEnd:=false;
	InitialOptions;
	readln(Cut);
	writeln(Cut);
	Clear;
	{ set to initial values, read instructions}
	ReadInstr;
	if Debug = dump then begin DumpTables; DumpMem(DInit); end;
	if Debug >= activity then WriteMem(DInit);
	Dummy:=0;
	OuterExec(1,DInit,false,0,Dummy,0);
	if Debug = dump then DumpMem(DInit);
end.
