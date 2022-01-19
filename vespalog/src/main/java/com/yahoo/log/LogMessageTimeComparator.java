// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Order LogMessage instances based on timestamp. The default ordering is ascending.
 * This may be reversed by the constructor argument.
 *
 * Note: this comparator imposes orderings that are inconsistent with equals.
 * This is due to only looking at the timestamp, so two different messages with
 * the same timestamp would appear "equal" to this comparator.
 *
 * @author Vidar Larsen
 */
public class LogMessageTimeComparator implements Comparator<LogMessage>, Serializable {
	private static final long serialVersionUID = 1L;

	/** Indicate the ordering of the timecomparison */
	private boolean ascending = true;

	/**
	 * Create a Time comparator for logmessages. Order is ascending.
	 *
	 */
	public LogMessageTimeComparator() {}

	/**
	 * Create a Time comparator for logmessages. The chronological order is dependent
	 * on the argument to the constructor.
	 *
	 * @param ascending true if you want LogMessages ordered ascending according to timestamp.
	 */
	public LogMessageTimeComparator(boolean ascending) {
		this.ascending = ascending;
	}

	public int compare(LogMessage message1, LogMessage message2) {
		return ascending ?
				message1.getTimestamp().compareTo(message2.getTimestamp())
				: message2.getTimestamp().compareTo(message1.getTimestamp());
	}
}
