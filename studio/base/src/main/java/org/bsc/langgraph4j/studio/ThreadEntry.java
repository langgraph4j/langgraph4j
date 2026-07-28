package org.bsc.langgraph4j.studio;

import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.AgentState;

import java.util.List;

/**
 * Represents an entry in a thread with its outputs.
 *
 * @param id the ID of the thread.
 * @param entries the outputs of the thread.
 */
record ThreadEntry(String id, List<? extends NodeOutput<? extends AgentState>> entries) {}
