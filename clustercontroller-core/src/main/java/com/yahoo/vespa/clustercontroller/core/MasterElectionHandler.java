// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.logging.Level;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;

import java.util.Map;
import java.util.logging.Logger;

/**
 * This class handles master election.
 */
public class MasterElectionHandler implements MasterInterface {

    private static final Logger log = Logger.getLogger(MasterElectionHandler.class.getName());

    private final Object monitor;
    private final Timer timer;
    private final int index;
    private int totalCount;
    private Integer masterCandidate; // The lowest indexed node in zookeeper
    private int nextInLineCount; // Our position in line of the nodes in zookeeper
    private int followers; // How many nodes are currently voting for the master candidate
    private Map<Integer, Integer> masterData;
    private Map<Integer, Integer> nextMasterData;
    private long masterGoneFromZooKeeperTime; // Set to time master fleet controller disappears from zookeeper
    private long masterZooKeeperCooldownPeriod; // The period in ms that we won't take over unless master come back.

    public MasterElectionHandler(int index, int totalCount, Object monitor, Timer timer) {
        this.monitor = monitor;
        this.timer = timer;
        this.index = index;
        this.totalCount = totalCount;
        this.nextInLineCount = Integer.MAX_VALUE;
        // Only a given set of nodes can ever become master
        if (index > (totalCount - 1) / 2) {
            log.log(Level.FINE, "Cluster controller " + index + ": We can never become master and will always stay a follower.");
        }
            // Tag current time as when we have not seen any other master. Make sure we're not taking over at once for master that is on the way down
        masterGoneFromZooKeeperTime = timer.getCurrentTimeInMillis();
    }

    public void setFleetControllerCount(int count) {
        totalCount = count;
        if (count == 1) {
            masterCandidate = 0;
            followers = 1;
            nextInLineCount = 0;
        }
    }

    public void setMasterZooKeeperCooldownPeriod(int period) {
        masterZooKeeperCooldownPeriod = period;
    }

    @Override
    public boolean isMaster() {
        Integer master = getMaster();
        return (master != null && master == index);
    }

    @Override
    public Integer getMaster() {
            // If too few followers there can be no master
        if (2 * followers <= totalCount) {
            return null;
        }
            // If all are following master candidate, it is master if it exists.
        if (followers == totalCount) {
            return masterCandidate;
        }
            // If not all are following we only accept master candidate if old master
            // disappeared sufficient time ago
        if (masterGoneFromZooKeeperTime + masterZooKeeperCooldownPeriod > timer.getCurrentTimeInMillis()) {
            return null;
        }
        return masterCandidate;
    }

    public String getMasterReason() {
        if (masterCandidate == null) {
            return "There is currently no master candidate.";
        }
        // If too few followers there can be no master
        if (2 * followers <= totalCount) {
            return "More than half of the nodes must agree for there to be a master. Only " + followers + " of "
                    + totalCount + " nodes agree on current master candidate (" + masterCandidate + ").";
        }
        // If all are following master candidate, it is master if it exists.
        if (followers == totalCount) {
            return "All " + totalCount + " nodes agree that " + masterCandidate + " is current master.";
        }

        // If not all are following we only accept master candidate if old master
        // disappeared sufficient time ago
        if (masterGoneFromZooKeeperTime + masterZooKeeperCooldownPeriod > timer.getCurrentTimeInMillis()) {
            return followers + " of " + totalCount + " nodes agree " + masterCandidate + " should be master, "
                    + "but old master cooldown period of " + masterZooKeeperCooldownPeriod + " ms has not passed yet. "
                    + "To ensure it has got time to realize it is no longer master before we elect a new one, "
                    + "currently there is no master.";
        }
        return followers + " of " + totalCount + " nodes agree " + masterCandidate + " is master.";
    }

    public boolean isAmongNthFirst(int first) { return (nextInLineCount < first); }

    public boolean watchMasterElection(DatabaseHandler database,
                                       DatabaseHandler.Context dbContext) throws InterruptedException {
        if (totalCount == 1) return false; // No point in doing master election with only one node configured to be cluster controller
        if (nextMasterData == null) {
            if (masterCandidate == null) {
                log.log(Level.FINEST, "Cluster controller " + index + ": No current master candidate. Waiting for data to do master election.");
            }
            return false; // Nothing have happened since last time.
        }
            // Move next data to temporary, such that we don't need to keep lock, and such that we don't retry
            // if we happen to fail processing the data.
        Map<Integer, Integer> state;
        log.log(Level.INFO, "Cluster controller " + index + ": Handling new master election, as we have received " + nextMasterData.size() + " entries");
        synchronized (monitor) {
            state = nextMasterData;
            nextMasterData = null;
        }
        log.log(Level.INFO, "Cluster controller " + index + ": Got master election state " + toString(state) + ".");
        if (state.isEmpty()) throw new IllegalStateException("Database has no master data. We should at least have data for ourselves.");
        Map.Entry<Integer, Integer> first = state.entrySet().iterator().next();
        Integer currentMaster = getMaster();
        if (currentMaster != null && first.getKey().intValue() != currentMaster.intValue()) {
            log.log(Level.INFO, "Cluster controller " + index + ": Master gone from ZooKeeper. Tagging timestamp. Will wait " + this.masterZooKeeperCooldownPeriod + " ms.");
            masterGoneFromZooKeeperTime = timer.getCurrentTimeInMillis();
            masterCandidate = null;
        }
        if (first.getValue().intValue() != first.getKey().intValue()) {
            log.log(Level.INFO, "Fleet controller " + index + ": First index is not currently trying to become master. Waiting for it to change state");
            masterCandidate = null;
            if (first.getKey() == index) {
                log.log(Level.INFO, "Cluster controller " + index + ": We are next in line to become master. Altering our state to look for followers");
                database.setMasterVote(dbContext, index);
            }
        } else {
            masterCandidate = first.getValue();
            followers = 0;
            for (Map.Entry<Integer, Integer> current : state.entrySet()) {
                if (current.getValue().intValue() == first.getKey().intValue()) {
                    ++followers;
                }
            }
            if (2 * followers > totalCount) {
                Integer newMaster = getMaster();
                if (newMaster != null && currentMaster != null && newMaster.intValue() == currentMaster.intValue()) {
                    log.log(Level.INFO, "MASTER_ELECTION: Cluster controller " + index + ": " + currentMaster + " is still the master");
                } else if (newMaster != null && currentMaster != null) {
                    log.log(Level.INFO, "MASTER_ELECTION: Cluster controller " + index + ": " + newMaster + " took over for fleet controller " + currentMaster + " as master");
                } else if (newMaster == null) {
                    log.log(Level.INFO, "MASTER_ELECTION: Cluster controller " + index + ": " + masterCandidate + " is new master candidate, but needs to wait before it can take over");
                }  else {
                    log.log(Level.INFO, "MASTER_ELECTION: Cluster controller " + index + ": " + newMaster + " is newly elected master");
                }
            } else {
                log.log(Level.INFO, "MASTER_ELECTION: Cluster controller " + index + ": Currently too few followers for cluster controller candidate " + masterCandidate + ". No current master. (" + followers + "/" + totalCount + " followers)");
            }
            Integer ourState = state.get(index);
            if (ourState == null) throw new IllegalStateException("Database lacks data from ourselves. This should always be present.");
            if (ourState.intValue() != first.getKey().intValue()) {
                log.log(Level.INFO, "Cluster controller " + index + ": Altering our state to follow new fleet controller master candidate " + first.getKey());
                database.setMasterVote(dbContext, first.getKey());
            }
        }
            // Only a given set of nodes can ever become master
        if (index <= (totalCount - 1) / 2) {
            int ourPosition = 0;
            for (Map.Entry<Integer, Integer> entry : state.entrySet()) {
                if (entry.getKey() != index) {
                    ++ourPosition;
                } else {
                    break;
                }
            }
            if (nextInLineCount != ourPosition) {
                nextInLineCount = ourPosition;
                if (ourPosition > 0) {
                    log.log(Level.FINE, "Cluster controller " + index + ": We are now " + getPosition(nextInLineCount) + " in queue to take over being master.");
                }
            }
        }
        masterData = state;
        return true;
    }

    private static String toString(Map<Integer, Integer> data) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : data.entrySet()) {
            sb.append(", ").append(entry.getKey()).append(" -> ").append(entry.getValue() == null ? "null" : entry.getValue());
        }
        if (sb.length() > 2) {
            sb.delete(0, 2);
        }
        sb.insert(0, "data(");
        sb.append(")");
        return sb.toString();
    }

    private String getPosition(int val) {
        if (val < 1) return "invalid(" + val + ")";
        if (val == 1) { return "first"; }
        if (val == 2) { return "second"; }
        if (val == 3) { return "third"; }
        return val + "th";
    }

    public void handleFleetData(Map<Integer, Integer> data) {
        log.log(Level.INFO, "Cluster controller " + index + ": Got new fleet data with " + data.size() + " entries: " + data);
        synchronized (monitor) {
            nextMasterData = data;
            monitor.notifyAll();
        }
    }

    public void lostDatabaseConnection() {
        if (totalCount > 1) {
            log.log(Level.INFO, "Cluster controller " + index + ": Clearing master data as we lost connection on node " + index);
            masterData = null;
            masterCandidate = null;
            followers = 0;
            nextMasterData = null;
        }
    }

    public void writeHtmlState(StringBuilder sb, int stateGatherCount) {
        sb.append("<h2>Master state</h2>\n");
        Integer master = getMaster();
        if (master != null) {
            sb.append("<p>Current cluster controller master is node " + master + ".");
            if (master.intValue() == index) sb.append(" (This node)");
            sb.append("</p>");
        } else {
            if (2 * followers <= totalCount) {
                sb.append("<p>There is currently no master. Less than half the fleet controllers (")
                  .append(followers).append(") are following master candidate ").append(masterCandidate)
                  .append(".</p>");
            } else if (masterGoneFromZooKeeperTime + masterZooKeeperCooldownPeriod > timer.getCurrentTimeInMillis()) {
                long time = timer.getCurrentTimeInMillis() - masterGoneFromZooKeeperTime;
                sb.append("<p>There is currently no master. Only " + (time / 1000) + " seconds have passed since")
                  .append(" old master disappeared. At least " + (masterZooKeeperCooldownPeriod / 1000) + " must pass")
                  .append(" before electing new master unless all possible master candidates are online.</p>");
            }
        }
        if ((master == null || master.intValue() != index) && nextInLineCount < stateGatherCount) {
            sb.append("<p>As we are number ").append(nextInLineCount)
                    .append(" in line for taking over as master, we're gathering state from nodes.</p>");
            sb.append("<p><font color=\"red\">As we are not the master, we don't know about nodes current system state"
                    + " or wanted states, so some statistics below may be stale. Look at status page on master "
                    + "for updated data.</font></p>");
        }
        if (index * 2 > totalCount) {
            sb.append("<p>As lowest index fleet controller is prioritized to become master, and more than half "
                    + "of the fleet controllers need to be available to select a master, we can never become master.</p>");
        }

            // Debug data
        sb.append("<p><font size=\"-1\" color=\"grey\">Master election handler internal state:")
          .append("<br>Index: " + index)
          .append("<br>Fleet controller count: " + totalCount)
          .append("<br>Master candidate: " + masterCandidate)
          .append("<br>Next in line count: " + nextInLineCount)
          .append("<br>Followers: " + followers)
          .append("<br>Master data:");
        if (masterData == null) {
            sb.append("null");
        } else {
            for (Map.Entry<Integer, Integer> e : masterData.entrySet()) {
                sb.append(" ").append(e.getKey()).append("->").append(e.getValue());
            }
        }
        sb.append("<br>Next master data:");
        if (nextMasterData == null) {
            sb.append("null");
        } else {
            for (Map.Entry<Integer, Integer> e : nextMasterData.entrySet()) {
                sb.append(" ").append(e.getKey()).append("->").append(e.getValue());
            }
        }
        sb.append("<br>Master gone from zookeeper time: " + masterGoneFromZooKeeperTime)
          .append("<br>Master cooldown period: " + masterZooKeeperCooldownPeriod)
          .append("</font></p>");
    }
}
