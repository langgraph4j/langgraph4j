package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class MemorySaver extends AbstractCheckpointSaver {

    private final Map<String, LinkedList<Checkpoint>> _checkpointsByThread = new HashMap<>();
    private final Map<String, TreeMap<Integer,Tag>> _tagsByThread = new HashMap<>();

    protected final Map<String, LinkedList<Checkpoint>> cache() {
        return Map.copyOf(_checkpointsByThread);
    }

    @Override
    protected final void insertedCheckpoint( RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
    }

    @Override
    protected final void updatedCheckpoint( RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
    }

    private Optional<Integer> lastVersion( TreeMap<Integer,Tag> prevTagsByThread) {
        return ofNullable(prevTagsByThread.lastEntry())
                .map(Map.Entry::getKey);
    }

    @Override
    protected final Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        final var threadId = threadId(config);

        final var prevTagsByThread = _tagsByThread
                .computeIfAbsent( threadId, k -> new TreeMap<>() );

        var lastThreadVersion = lastVersion(prevTagsByThread).orElse(0);

        final var tag =  new Tag( threadId(config),
                                    ++lastThreadVersion,
                                    _checkpointsByThread.remove( threadId ) );

        prevTagsByThread.put( lastThreadVersion, tag );

        return tag;
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        final var threadId = threadId(config);

        return _checkpointsByThread.computeIfAbsent(threadId, k -> new LinkedList<>());

    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception {
        requireNonNull(config, "config cannot be null");
        return ofNullable(_tagsByThread.get(threadId(config)))
                .map( tagsByVersion ->
                        ofNullable(version).map(tagsByVersion::get)
                                .orElseGet( () -> tagsByVersion.lastEntry().getValue() ));


    }
}
