------------------------------ MODULE bucketinfo ------------------------------

EXTENDS Naturals, FiniteSets, Sequences, Integers, TLC

(***************************************************************************)
(* This spec models the state synchronization mechanisms for a single data *)
(* bucket between distributors and a single content node. The core         *)
(* assumption is that if this mechanism is correct for one bucket,         *)
(* it will be correct for any larger set of buckets (assuming bucket       *)
(* independence, which is a half-truth due to splits and joins in an       *)
(* actual system). It is important to  note that this only models          *)
(* single-replica eventual consistency. It is not intended to handle       *)
(* cross-replica consistency.                                              *)
(*                                                                         *)
(* This specification is explicitly bounded in its state space and is      *)
(* therefore only able to model a subset of the infinite state space of    *)
(* a real system. Such is life.                                            *)
(*                                                                         *)
(* Example configuration:                                                  *)
(*                                                                         *)
(*   ContentNode       <- [model value]                                    *)
(*   DistributorNodes  <- [model value] <symmetrical> {D1, D2}             *)
(*   MutatingOps       <- [model value] <symmetrical> {M1, M2}             *)
(*   Null              <- [model value]                                    *)
(*   ClusterStates     <- {1, 2}                                           *)
(*   NodeEpochs        <- {1}                                              *)
(*                                                                         *)
(* State space explosion warning: letting NodeEpochs have a cardinality    *)
(* higher than 1 will send the number of explorable states to the moon,    *)
(* as the content node may then "restart" at any possible time.            *)
(* Example (exact counts may be out of date):                              *)
(*                                                                         *)
(* The above configuration yields 358,189 states.                          *)
(*                                                                         *)
(* Changing NodeEpochs to {1,2} yields 2,326,694,797 states.               *)
(*                                                                         *)
(* Note: this spec represents a possible future state of the system, it    *)
(* does not represent the current working state.                           *)
(***************************************************************************)

CONSTANTS DistributorNodes, ContentNode, ClusterStates,
          NodeEpochs, MutatingOps, Null

ASSUME /\ IsFiniteSet(DistributorNodes)
       /\ Cardinality(DistributorNodes) > 0
       /\ IsFiniteSet(ClusterStates)
       /\ Cardinality(ClusterStates) > 0
       /\ IsFiniteSet(NodeEpochs)
       /\ Cardinality(NodeEpochs) > 0
       /\ IsFiniteSet(MutatingOps)
       /\ Cardinality(MutatingOps) > 0
       /\ ClusterStates \subseteq (Nat \ {0})
       /\ NodeEpochs \subseteq (Nat \ {0})
       /\ DistributorNodes \intersect {ContentNode} = {}

(*--algorithm bucketinfo
variables
  proposedMuts = {},
  publishedStates = {},
  storEpoch \in NodeEpochs,
  messages = {}; \* model messages as unordered set to test reordering "for free"

define
  SeqToSet(s) == {s[i]: i \in 1..Len(s)}

  HasMessage(t, d) == \E m \in messages: (m.type = t /\ m.dest = d)

  MessagesOfTypeTo(t, d) == {m \in messages: m.type = t /\ m.dest = d}

  UnpublishedStateVersions == {s \in ClusterStates:
                                 \A p \in publishedStates: s > p.version}

  Max(s) == CHOOSE x \in s: \A y \in s: x >= y

  NullDistributorDbState == [muts |-> <<>>, epoch |-> 0, seqNo |-> 0]
end define;

macro Send(msgs) begin
  messages := messages \union msgs;
end macro;

macro MarkReceivedAndSend(recvd, msgs) begin
  messages := (messages \union msgs) \ recvd;
end macro;

macro MarkReceived(msg) begin
  messages := messages \ {msg};
end macro;

macro EnqueueMutMsg(msg) begin
  mutQ := mutQ \union {msg};
end macro;

macro DequeueMutMsg(msg) begin
  mutQ := mutQ \ {msg};
end macro;

(***************************************************************************)
(* The cluster controller may publish new cluster states at any time, and  *)
(* these will eventually be picked up by the node processes. The new       *)
(* cluster state will have a higher version than any previously published  *)
(* states and includes the distributor that owns the bucket in the         *)
(* given state. The owning distributor in any given state is               *)
(* non-deterministic.                                                      *)
(***************************************************************************)

fair process ClusterController = "cc"
begin CCPublish:
  while TRUE do
    either
      with v \in UnpublishedStateVersions, d \in DistributorNodes do
        publishedStates := publishedStates \union {[version |-> v, owner |-> d]};
      end with
    or
      skip;
    end either;
  end while;
end process;

(***************************************************************************)
(* Distributor processes manage a disjoint subset of the bucket space for  *)
(* any given cluster state version. For our single bucket model, only one  *)
(* of the distributors is designated as the bucket owner for any given     *)
(* state.                                                                  *)
(*                                                                         *)
(* A distributor process that is the owner in its most recent state may    *)
(* propose a mutation to the bucket.                                       *)
(*                                                                         *)
(* It is expected (and this specification verifies) that if a distributor  *)
(* owns a bucket in a state, it will eventually observe the actual state   *)
(* of the bucket as it exists on the content node.                         *)
(***************************************************************************)

fair+ process Distributor \in DistributorNodes
variables
  curStateD = [version |-> 0, epoch |-> 0, owner |-> Null],
  pendingStateD = Null,
  distDbState = NullDistributorDbState; \* mutations for a single bucket
begin DEventLoop:
  while TRUE do
    either
      (*********************************************************************)
      (* Process new cluster state, if one has been published. This also   *)
      (* emulates the special case where a content node has restarted and  *)
      (* the cluster controller lets distributors know by sending the      *)
      (* node's start timestamp (epoch) as metadata in the cluster state.  *)
      (* This can happen even without a new cluster state version being    *)
      (* published, so we treat the two as separate events. We assume      *)
      (* that distributors will eventually observe the new epoch.          *)
      (*                                                                   *)
      (* A new cluster state (or epoch) triggers a bucket info request     *)
      (* towards the content node, as the distributor may have outdated    *)
      (* (or non-existing) metadata about its bucket(s). In this           *)
      (* transition period between receiving the cluster state and         *)
      (* applying the bucket information required to handle operations     *)
      (* for it, the distributor explicitly marks the state as pending.    *)
      (*                                                                   *)
      (* The distributor always purges all knowledge of buckets it is      *)
      (* not supposed to manage (own) as part of the cluster state         *)
      (* observation edge.                                                 *)
      (*********************************************************************)
      with s \in {p \in publishedStates:
          \/ p.version > curStateD.version
          \/ /\ p.version = curStateD.version
             /\ storEpoch > curStateD.epoch} do
        if \/ pendingStateD = Null
           \/ pendingStateD.cs.version < s.version
           \/ pendingStateD.epoch < storEpoch then
          Send({[type    |-> "bucket-info-request",
                 src     |-> self,
                 version |-> s.version,
                 dest    |-> ContentNode]});
          pendingStateD := [cs |-> s, epoch |-> storEpoch];
          if s.owner # self then
            \* "as-if" purged state for non-owned bucket
            distDbState := NullDistributorDbState;
          end if;
        end if;
      end with;
    (***********************************************************************)
    (* The below states are only transitively reachable if we have         *)
    (* observed at least one state                                         *)
    (***********************************************************************)
    or
      (*********************************************************************)
      (* If we are the owner in what we believe is the most recent state,  *)
      (* send a mutating operation. The content node may reject this       *)
      (* operation if it disagrees with the current state.                 *)
      (*********************************************************************)
      with op \in (MutatingOps \ proposedMuts) do
        if curStateD.owner = self then
          proposedMuts := proposedMuts \union {op};
          Send({[type |-> "mutation-request",
                 src  |-> self,
                 dest |-> ContentNode,
                 mut  |-> op]});
        end if;
      end with;
    or
      (*********************************************************************)
      (* Receive mutation response. Only apply to state iff it represents  *)
      (* a more recent state than what we already have.                    *)
      (*********************************************************************)
      with m \in MessagesOfTypeTo("mutation-response", self) do
        MarkReceived(m);
        if /\ m.ack
           /\ (pendingStateD # Null) => (pendingStateD.cs.owner = self)
           /\ (pendingStateD = Null) => (curStateD.owner = self)
           /\ \/ m.epoch > distDbState.epoch
              \/ /\ m.epoch = distDbState.epoch
                 /\ m.state.seqNo > distDbState.seqNo then
          distDbState := [muts  |-> m.state.muts,
                          epoch |-> m.epoch,
                          seqNo |-> m.state.seqNo];
        end if;
      end with;
    or
      (*********************************************************************)
      (* Process bucket info returned from the content node.               *)
      (*                                                                   *)
      (* In today's actual implementation we explicitly differentiate      *)
      (* between full bucket fetches (which are used for state             *)
      (* transitions) and single-bucket fetches (which are not).           *)
      (* Since we only have a single bucket we muddle the waters a bit     *)
      (* by having one operation with the semantics of both.               *)
      (*********************************************************************)
      with m \in MessagesOfTypeTo("bucket-info-response", self) do
        with effVer = IF pendingStateD # Null
                      THEN pendingStateD.cs.version
                      ELSE curStateD.version do
          if m.version = effVer then
            MarkReceived(m);
            if pendingStateD # Null then
              assert(curStateD.version <= pendingStateD.cs.version);
              curStateD := [version |-> pendingStateD.cs.version,
                            owner   |-> pendingStateD.cs.owner,
                            epoch   |-> pendingStateD.epoch];
              pendingStateD := Null;
            end if;
            if /\ m.ack /\ \/ m.epoch > distDbState.epoch
                           \/ /\ m.epoch = distDbState.epoch
                              /\ m.state.seqNo > distDbState.seqNo then
              distDbState := [muts  |-> m.state.muts,
                              epoch |-> m.epoch,
                              seqNo |-> m.state.seqNo];
            end if;
          else
            \* Resend; expect stale node to eventually catch up
            if m.version < effVer then
              MarkReceivedAndSend({m}, {
                [type    |-> "bucket-info-request",
                 src     |-> self,
                 version |-> effVer,
                 dest    |-> ContentNode]});
            else
              MarkReceived(m);
            end if;
          end if;
        end with;
      end with;
    or
      (*********************************************************************)
      (* Not modelling distributor restart, as that always refreshes       *)
      (* metadata state anyway.                                            *)
      (*********************************************************************)
      skip;
    end either;
  end while;
end process;

(***************************************************************************)
(* The content node process responds to bucket info and mutation requests  *)
(* from the distributors, as well as listening for cluster state changes.  *)
(*                                                                         *)
(* The content node has a persistence operation queue for mutating         *)
(* operations. We model this queue explicitly as a set to simulate         *)
(* reordering caused by differing operation priorities etc.                *)
(***************************************************************************)

fair process Content = "content"
variables
  curStateS = [version |-> 0, owner |-> Null],
  ephemeralSeqNo = 1,
  mutQ = {},
  storDbState = [muts |-> <<>>, seqNo |-> 0];
begin CEventLoop:
  while TRUE do
    either
      (*********************************************************************)
      (* Apply new cluster state                                           *)
      (*********************************************************************)
      with s \in {p \in publishedStates: p.version > curStateS.version} do
        curStateS := s;
      end with
    or
      (*********************************************************************)
      (* Process bucket info requests from distributors in the context     *)
      (* of the node's currently applied cluster state.                    *)
      (*********************************************************************)
      with m \in MessagesOfTypeTo("bucket-info-request", ContentNode) do
        with acked = /\ m.version = curStateS.version
                     /\ m.src = curStateS.owner do
          MarkReceivedAndSend({m}, {
            [type    |-> "bucket-info-response",
             src     |-> ContentNode,
             dest    |-> m.src,
             ack     |-> acked,
             version |-> curStateS.version,
             epoch   |-> storEpoch,
             state   |-> storDbState]});
        end with;
      end with;
    or
      (*********************************************************************)
      (* Add incoming mutation request to persistence queue. No checking   *)
      (* is performed at this point.                                       *)
      (*********************************************************************)
      with m \in MessagesOfTypeTo("mutation-request", ContentNode) do
        MarkReceived(m);
        EnqueueMutMsg(m);
      end with;
    or
      (*********************************************************************)
      (* Process queued mutation. Apply operation iff the sender is the    *)
      (* owner in the current cluster state.                               *)
      (*********************************************************************)
      with qm \in mutQ do
        DequeueMutMsg(qm);
        with acked = (qm.src = curStateS.owner) do
          if acked = TRUE then
            storDbState := [muts  |-> Append(storDbState.muts, qm.mut),
                            seqNo |-> ephemeralSeqNo];
            ephemeralSeqNo := ephemeralSeqNo + 1;
          end if;
          Send({
            [type  |-> "mutation-response",
             src   |-> ContentNode,
             dest  |-> qm.src,
             ack   |-> acked,
             epoch |-> storEpoch,
             state |-> storDbState]});
        end with;
      end with;
    or
      (*********************************************************************)
      (* Restart node (if we "can"). Restarting resets all non-persisted   *)
      (* state to their initial values and only preserves acknowledged     *)
      (* mutations.                                                        *)
      (*                                                                   *)
      (* Note that for simplicity we do not clear the persistence queue.   *)
      (* Ops are implicitly aborted depending on whether the state is      *)
      (* evaluated before or after the content node gets hold of the       *)
      (* current cluster state.                                            *)
      (*********************************************************************)
      with newEpoch \in {e \in NodeEpochs: e > storEpoch} do
        ephemeralSeqNo := 1;
        storEpoch      := newEpoch;
        curStateS      := [version |-> 0, owner |-> Null];
        storDbState    := [muts |-> storDbState.muts, seqNo |-> 0];
      end with;
    or
      \* TODO notify change?
      skip;
    end either;
  end while;
end process;

end algorithm;*)


\* BEGIN TRANSLATION (chksum(pcal) = "7a179595" /\ chksum(tla) = "2d89f470")
VARIABLES proposedMuts, publishedStates, storEpoch, messages

(* define statement *)
SeqToSet(s) == {s[i]: i \in 1..Len(s)}

HasMessage(t, d) == \E m \in messages: (m.type = t /\ m.dest = d)

MessagesOfTypeTo(t, d) == {m \in messages: m.type = t /\ m.dest = d}

UnpublishedStateVersions == {s \in ClusterStates:
                               \A p \in publishedStates: s > p.version}

Max(s) == CHOOSE x \in s: \A y \in s: x >= y

NullDistributorDbState == [muts |-> <<>>, epoch |-> 0, seqNo |-> 0]

VARIABLES curStateD, pendingStateD, distDbState, curStateS, ephemeralSeqNo, 
          mutQ, storDbState

vars == << proposedMuts, publishedStates, storEpoch, messages, curStateD, 
           pendingStateD, distDbState, curStateS, ephemeralSeqNo, mutQ, 
           storDbState >>

ProcSet == {"cc"} \cup (DistributorNodes) \cup {"content"}

Init == (* Global variables *)
        /\ proposedMuts = {}
        /\ publishedStates = {}
        /\ storEpoch \in NodeEpochs
        /\ messages = {}
        (* Process Distributor *)
        /\ curStateD = [self \in DistributorNodes |-> [version |-> 0, epoch |-> 0, owner |-> Null]]
        /\ pendingStateD = [self \in DistributorNodes |-> Null]
        /\ distDbState = [self \in DistributorNodes |-> NullDistributorDbState]
        (* Process Content *)
        /\ curStateS = [version |-> 0, owner |-> Null]
        /\ ephemeralSeqNo = 1
        /\ mutQ = {}
        /\ storDbState = [muts |-> <<>>, seqNo |-> 0]

ClusterController == /\ \/ /\ \E v \in UnpublishedStateVersions:
                                \E d \in DistributorNodes:
                                  publishedStates' = (publishedStates \union {[version |-> v, owner |-> d]})
                        \/ /\ TRUE
                           /\ UNCHANGED publishedStates
                     /\ UNCHANGED << proposedMuts, storEpoch, messages, 
                                     curStateD, pendingStateD, distDbState, 
                                     curStateS, ephemeralSeqNo, mutQ, 
                                     storDbState >>

Distributor(self) == /\ \/ /\ \E s \in        {p \in publishedStates:
                                       \/ p.version > curStateD[self].version
                                       \/ /\ p.version = curStateD[self].version
                                          /\ storEpoch > curStateD[self].epoch}:
                                IF \/ pendingStateD[self] = Null
                                   \/ pendingStateD[self].cs.version < s.version
                                   \/ pendingStateD[self].epoch < storEpoch
                                   THEN /\ messages' = (messages \union ({[type    |-> "bucket-info-request",
                                                                           src     |-> self,
                                                                           version |-> s.version,
                                                                           dest    |-> ContentNode]}))
                                        /\ pendingStateD' = [pendingStateD EXCEPT ![self] = [cs |-> s, epoch |-> storEpoch]]
                                        /\ IF s.owner # self
                                              THEN /\ distDbState' = [distDbState EXCEPT ![self] = NullDistributorDbState]
                                              ELSE /\ TRUE
                                                   /\ UNCHANGED distDbState
                                   ELSE /\ TRUE
                                        /\ UNCHANGED << messages, 
                                                        pendingStateD, 
                                                        distDbState >>
                           /\ UNCHANGED <<proposedMuts, curStateD>>
                        \/ /\ \E op \in (MutatingOps \ proposedMuts):
                                IF curStateD[self].owner = self
                                   THEN /\ proposedMuts' = (proposedMuts \union {op})
                                        /\ messages' = (messages \union ({[type |-> "mutation-request",
                                                                           src  |-> self,
                                                                           dest |-> ContentNode,
                                                                           mut  |-> op]}))
                                   ELSE /\ TRUE
                                        /\ UNCHANGED << proposedMuts, messages >>
                           /\ UNCHANGED <<curStateD, pendingStateD, distDbState>>
                        \/ /\ \E m \in MessagesOfTypeTo("mutation-response", self):
                                /\ messages' = messages \ {m}
                                /\ IF /\ m.ack
                                      /\ (pendingStateD[self] # Null) => (pendingStateD[self].cs.owner = self)
                                      /\ (pendingStateD[self] = Null) => (curStateD[self].owner = self)
                                      /\ \/ m.epoch > distDbState[self].epoch
                                         \/ /\ m.epoch = distDbState[self].epoch
                                            /\ m.state.seqNo > distDbState[self].seqNo
                                      THEN /\ distDbState' = [distDbState EXCEPT ![self] = [muts  |-> m.state.muts,
                                                                                            epoch |-> m.epoch,
                                                                                            seqNo |-> m.state.seqNo]]
                                      ELSE /\ TRUE
                                           /\ UNCHANGED distDbState
                           /\ UNCHANGED <<proposedMuts, curStateD, pendingStateD>>
                        \/ /\ \E m \in MessagesOfTypeTo("bucket-info-response", self):
                                LET effVer == IF pendingStateD[self] # Null
                                              THEN pendingStateD[self].cs.version
                                              ELSE curStateD[self].version IN
                                  IF m.version = effVer
                                     THEN /\ messages' = messages \ {m}
                                          /\ IF pendingStateD[self] # Null
                                                THEN /\ Assert((curStateD[self].version <= pendingStateD[self].cs.version), 
                                                               "Failure of assertion at line 234, column 15.")
                                                     /\ curStateD' = [curStateD EXCEPT ![self] = [version |-> pendingStateD[self].cs.version,
                                                                                                  owner   |-> pendingStateD[self].cs.owner,
                                                                                                  epoch   |-> pendingStateD[self].epoch]]
                                                     /\ pendingStateD' = [pendingStateD EXCEPT ![self] = Null]
                                                ELSE /\ TRUE
                                                     /\ UNCHANGED << curStateD, 
                                                                     pendingStateD >>
                                          /\ IF /\ m.ack /\ \/ m.epoch > distDbState[self].epoch
                                                            \/ /\ m.epoch = distDbState[self].epoch
                                                               /\ m.state.seqNo > distDbState[self].seqNo
                                                THEN /\ distDbState' = [distDbState EXCEPT ![self] = [muts  |-> m.state.muts,
                                                                                                      epoch |-> m.epoch,
                                                                                                      seqNo |-> m.state.seqNo]]
                                                ELSE /\ TRUE
                                                     /\ UNCHANGED distDbState
                                     ELSE /\ IF m.version < effVer
                                                THEN /\ messages' = (messages \union (                       {
                                                                    [type    |-> "bucket-info-request",
                                                                     src     |-> self,
                                                                     version |-> effVer,
                                                                     dest    |-> ContentNode]})) \ ({m})
                                                ELSE /\ messages' = messages \ {m}
                                          /\ UNCHANGED << curStateD, 
                                                          pendingStateD, 
                                                          distDbState >>
                           /\ UNCHANGED proposedMuts
                        \/ /\ TRUE
                           /\ UNCHANGED <<proposedMuts, messages, curStateD, pendingStateD, distDbState>>
                     /\ UNCHANGED << publishedStates, storEpoch, curStateS, 
                                     ephemeralSeqNo, mutQ, storDbState >>

Content == /\ \/ /\ \E s \in {p \in publishedStates: p.version > curStateS.version}:
                      curStateS' = s
                 /\ UNCHANGED <<storEpoch, messages, ephemeralSeqNo, mutQ, storDbState>>
              \/ /\ \E m \in MessagesOfTypeTo("bucket-info-request", ContentNode):
                      LET acked == /\ m.version = curStateS.version
                                   /\ m.src = curStateS.owner IN
                        messages' = (messages \union (                       {
                                    [type    |-> "bucket-info-response",
                                     src     |-> ContentNode,
                                     dest    |-> m.src,
                                     ack     |-> acked,
                                     version |-> curStateS.version,
                                     epoch   |-> storEpoch,
                                     state   |-> storDbState]})) \ ({m})
                 /\ UNCHANGED <<storEpoch, curStateS, ephemeralSeqNo, mutQ, storDbState>>
              \/ /\ \E m \in MessagesOfTypeTo("mutation-request", ContentNode):
                      /\ messages' = messages \ {m}
                      /\ mutQ' = (mutQ \union {m})
                 /\ UNCHANGED <<storEpoch, curStateS, ephemeralSeqNo, storDbState>>
              \/ /\ \E qm \in mutQ:
                      /\ mutQ' = mutQ \ {qm}
                      /\ LET acked == (qm.src = curStateS.owner) IN
                           /\ IF acked = TRUE
                                 THEN /\ storDbState' = [muts  |-> Append(storDbState.muts, qm.mut),
                                                         seqNo |-> ephemeralSeqNo]
                                      /\ ephemeralSeqNo' = ephemeralSeqNo + 1
                                 ELSE /\ TRUE
                                      /\ UNCHANGED << ephemeralSeqNo, 
                                                      storDbState >>
                           /\ messages' = (messages \union (   {
                                           [type  |-> "mutation-response",
                                            src   |-> ContentNode,
                                            dest  |-> qm.src,
                                            ack   |-> acked,
                                            epoch |-> storEpoch,
                                            state |-> storDbState']}))
                 /\ UNCHANGED <<storEpoch, curStateS>>
              \/ /\ \E newEpoch \in {e \in NodeEpochs: e > storEpoch}:
                      /\ ephemeralSeqNo' = 1
                      /\ storEpoch' = newEpoch
                      /\ curStateS' = [version |-> 0, owner |-> Null]
                      /\ storDbState' = [muts |-> storDbState.muts, seqNo |-> 0]
                 /\ UNCHANGED <<messages, mutQ>>
              \/ /\ TRUE
                 /\ UNCHANGED <<storEpoch, messages, curStateS, ephemeralSeqNo, mutQ, storDbState>>
           /\ UNCHANGED << proposedMuts, publishedStates, curStateD, 
                           pendingStateD, distDbState >>

Next == ClusterController \/ Content
           \/ (\E self \in DistributorNodes: Distributor(self))

Spec == /\ Init /\ [][Next]_vars
        /\ WF_vars(ClusterController)
        /\ \A self \in DistributorNodes : SF_vars(Distributor(self))
        /\ WF_vars(Content)

\* END TRANSLATION

(***************************************************************************)
(* Safety invariants of the specification                                  *)
(***************************************************************************)

(***************************************************************************)
(* Only proposed mutations may be added to the DB states                   *)
(***************************************************************************)

NonTriviality == /\ SeqToSet(storDbState.muts) \subseteq proposedMuts
                 /\ \A d \in DistributorNodes:
                     SeqToSet(distDbState[d].muts) \subseteq proposedMuts
                 /\ \A m \in mutQ: m.mut \in proposedMuts

(***************************************************************************)
(* Type safety invariants                                                  *)
(***************************************************************************)

AllowedMessages ==
           [type: {"bucket-info-request"}, src: DistributorNodes,
            version: Nat \ {0}, dest: {ContentNode}]
    \union [type: {"bucket-info-response"}, src: {ContentNode},
            dest: DistributorNodes, ack: BOOLEAN, version: Nat,
            epoch: NodeEpochs,
            state: [muts: Seq(MutatingOps), seqNo: Nat]]
    \union [type: {"mutation-request"}, src: DistributorNodes,
            dest: {ContentNode}, mut: MutatingOps]
    \union [type: {"mutation-response"}, src: {ContentNode},
            dest: DistributorNodes, ack: BOOLEAN, epoch: NodeEpochs,
            state: [muts: Seq(MutatingOps), seqNo: Nat]]

TypeOK == /\ publishedStates \subseteq [version: ClusterStates,
                                        owner: DistributorNodes]
          /\ ephemeralSeqNo \in (Nat \ {0})
          /\ storEpoch \in NodeEpochs
          /\ proposedMuts \subseteq MutatingOps
          /\ mutQ \subseteq AllowedMessages
          /\ messages \subseteq AllowedMessages
          /\ \A d \in DistributorNodes:
              /\ curStateD[d] \in [version: ClusterStates \union {0},
                                   epoch: NodeEpochs \union {0},
                                   owner: DistributorNodes \union {Null}]
              /\ distDbState[d] \in [muts: Seq(MutatingOps),
                                     epoch: NodeEpochs \union {0},
                                     seqNo: Nat]
          /\ curStateS \in [version: ClusterStates \union {0},
                            owner: DistributorNodes \union {Null}]
          /\ storDbState \in [muts: Seq(MutatingOps), seqNo: Nat]


(***************************************************************************)
(* Temporal invariants of the specification                                *)
(***************************************************************************)

(***************************************************************************)
(* If a distributor has converged to the last possible cluster state, it   *)
(* must eventually (<>) converge to a stable ([]) database state that      *)
(* matches that of the content node.                                       *)
(*                                                                         *)
(* Note that this temporal invariant is much more expensive (10x) than     *)
(* just testing non-temporal invariants.                                   *)
(***************************************************************************)

EventualConsistency == \A d \in DistributorNodes:
                         []((/\ curStateD[d].version = Max(ClusterStates)
                             /\ curStateD[d].owner = d)
                                 => <>[](distDbState[d] = storDbState))

(***************************************************************************)
(* For all reachable states, if a distributor does not either own the      *)
(* bucket in the active cluster state or in the pending state, it should   *)
(* never have the bucket in its database state (not even transiently).     *)
(***************************************************************************)

NoResurrection ==
  \A d \in DistributorNodes:
    (/\ pendingStateD[d] # Null => pendingStateD[d].cs.owner # d
     /\ pendingStateD[d] = Null => curStateD[d].owner # d)
         => distDbState[d] = NullDistributorDbState

(***************************************************************************)
(* Proper temporal invariants (i.e. EventualConsistency) are very          *)
(* expensive, so try to fake them by explicitly checking for the desired   *)
(* distributor states when the cluster is effectively quiescent, i.e.      *)
(* distributors have converged to the last possible state and there are no *)
(* more states that can trigger further event edges.                       *)
(***************************************************************************)

QuiescenceImpliesConsistency ==
  \A d \in DistributorNodes:
    (/\ curStateD[d].version = Max(ClusterStates)
     /\ curStateD[d].epoch = storEpoch
     /\ \A m \in messages: (m.src # d /\ m.dest # d)
     /\ mutQ = {}
     /\ proposedMuts = MutatingOps)
         => \/ /\ curStateD[d].owner = d
               /\ distDbState[d].muts = storDbState.muts
            \/ /\ curStateD[d].owner # d
               /\ distDbState[d] = NullDistributorDbState

=============================================================================
