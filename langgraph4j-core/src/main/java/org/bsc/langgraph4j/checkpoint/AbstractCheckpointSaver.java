package org.bsc.langgraph4j.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.utils.TryFunction;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public abstract class AbstractCheckpointSaver implements BaseCheckpointSaver {
    private final ReentrantLock _lock = new ReentrantLock();
    private final Map<String, TreeMap<String,BaseCheckpointSaver>> _subGraphSaversByThread = new HashMap<>();

    protected abstract LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception;

    protected abstract void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception;

    protected abstract void updatedCheckpoint( RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception;

    protected abstract Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints ) throws Exception;

    private <T> T loadOrInitCheckpoints(RunnableConfig config,
                                                TryFunction<LinkedList<Checkpoint>, T, Exception> transformer) throws Exception {
        _lock.lock();
        try {
            final var checkpoints = loadCheckpoints(config);

            return transformer.tryApply( checkpoints );
        } finally {
            _lock.unlock();
        }

    }

    final Optional<Checkpoint> getLast( LinkedList<Checkpoint> checkpoints, RunnableConfig config ) {
        return (checkpoints.isEmpty() ) ? Optional.empty() : ofNullable(checkpoints.peek());
    }

    @Override
    public final Collection<Checkpoint> list(RunnableConfig config ) {
        try {
            return loadOrInitCheckpoints( config, List::copyOf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final Optional<Checkpoint> get(RunnableConfig config) {

        try {
            return loadOrInitCheckpoints( config, checkpoints -> {
                if( config.checkPointId().isPresent() ) {
                    return config.checkPointId()
                            .flatMap( id -> checkpoints.stream()
                                    .filter( checkpoint -> checkpoint.getId().equals(id) )
                                    .findFirst());
                }
                return getLast(checkpoints,config);

            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {

        return loadOrInitCheckpoints( config, checkpoints -> {

            if (config.checkPointId().isPresent()) { // Replace Checkpoint
                String checkPointId = config.checkPointId().get();
                int index = IntStream.range(0, checkpoints.size())
                        .filter(i -> checkpoints.get(i).getId().equals(checkPointId))
                        .findFirst()
                        .orElseThrow(() -> (new NoSuchElementException(format("Checkpoint with id %s not found!", checkPointId))));
                checkpoints.set(index, checkpoint );
                updatedCheckpoint( config, checkpoints, checkpoint);
                return config;
            }

            checkpoints.push( checkpoint ); // Add Checkpoint
            insertedCheckpoint( config, checkpoints, checkpoint);

            return RunnableConfig.builder(config)
                    .checkPointId(checkpoint.getId())
                    .build();

        });
    }

    @Override
    public final Tag release(RunnableConfig config) throws Exception {

        return loadOrInitCheckpoints( config, checkpoints -> {

            final var subGraphSaversByThread = _subGraphSaversByThread.remove( threadId(config) );
            if( subGraphSaversByThread != null ) {
                for( var entry: subGraphSaversByThread.entrySet() ) {
                    final var subThreadId = entry.getKey();
                    final var subGraphSaver = entry.getValue();

                    final var subGraphConfig = RunnableConfig.builder(config)
                            .threadId(subThreadId)
                            .build();

                    subGraphSaver.release( subGraphConfig );
                }
            }
            return releaseCheckpoints( config, checkpoints );
        });
    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception {
        return Optional.empty();
    }

    @Override
    public void putSubGraphSaver(RunnableConfig parentConfig, RunnableConfig subGraphConfig, BaseCheckpointSaver subGraphSaver) {
        requireNonNull( subGraphSaver, "subGraphSaver cannot be null" );
        final var parentThreadId = threadId( requireNonNull(parentConfig, "parentConfig cannot be null"));
        final var subThreadId = threadId( requireNonNull(subGraphConfig, "subGraphConfig cannot be null") );

        _lock.lock();
        try {
            final var subGraphSaversByThread = _subGraphSaversByThread
                    .computeIfAbsent( parentThreadId, k -> new TreeMap<>() );

            subGraphSaversByThread.put( subThreadId,subGraphSaver );
        } finally {
            _lock.unlock();
        }
    }

    @Override
    public Collection<SubGraphSaver> listSubGraphSaver(RunnableConfig parentConfig) {
        final var parentThreadId = threadId( requireNonNull(parentConfig, "parentConfig cannot be null"));

        final var subGraphSaversByThread = _subGraphSaversByThread.get( parentThreadId );

        if( subGraphSaversByThread == null ) {
            return List.of();
        }

        return subGraphSaversByThread.entrySet().stream()
                .map( entry -> new SubGraphSaver( entry.getKey(), entry.getValue() ) )
                .toList();
    }
}
