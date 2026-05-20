package org.bsc.langgraph4j;


import org.bsc.async.AsyncGenerator;
import org.bsc.async.AsyncGeneratorFlow;
import org.bsc.langgraph4j.utils.ExceptionUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static org.junit.jupiter.api.Assertions.*;

public class AsyncGeneratorTest {

    @Test
    public void asyncForEachAsyncTest() throws Exception {

        var myArray = List.of( "e1", "e2", "e3", "e4", "e5" );

        final var it = AsyncGenerator.from(myArray.iterator());

        List<String> result = new ArrayList<>();

        it.forEachAsync( result::add ).thenAccept( t -> {
            System.out.println( "Finished");
        }).join();

        for (String i : it) {
            result.add(i);
            System.out.println(i);
        }
        System.out.println( "Finished");

        assertEquals(myArray.size(), result.size() );
        assertIterableEquals( myArray, result );
    }

    @Test
    public void asyncForEachAsyncCancelTest() throws Exception {

        var myArray = List.of( "e1", "e2", "e3", "e4", "e5" );

        try( final var it = AsyncGeneratorFlow.<String>create( d ->  {

            for( var e : myArray ) {
                d.dispatchAsync( AsyncGenerator.Data.of( completedFuture(e) ) );
            }
            d.dispatchAsync( AsyncGenerator.Data.done() );

        })) {

            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(400);
                    it.cancel(true);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            final List<String> result = new ArrayList<>();

            it.forEachAsync(value -> {
                        try {
                            Thread.sleep(100);
                            result.add(value);
                            System.out.println(value);
                        } catch (InterruptedException e) {
                            throw new CancellationException("Cancelled");
                        }
                    })
                    .thenAccept(t -> {
                        fail("Should not reach end of iteration");
                        System.out.println("Finished");
                    })
                    .exceptionally(ex -> {
                        assertInstanceOf(CancellationException.class, ex.getCause());
                        System.out.println(ex.getCause().getMessage());
                        return null;
                    })
                    .join();

            var sizeAfterInterruption = result.size();
            assertNotEquals(myArray.size(), result.size());

            for (String i : it) {
                result.add(i);
                System.out.println(i);
            }
            System.out.println("Finished");

            assertEquals(sizeAfterInterruption, result.size());
        }
    }

    @Test
    public void asyncQueueTest() throws Exception {

        try(final var it = AsyncGeneratorFlow.<String>create(d -> {
            for( int i = 0 ; i < 10 ; ++i ) {
                d.dispatchAsync( AsyncGenerator.Data.of( completedFuture("e"+i )) );
            }
            d.dispatchAsync( AsyncGenerator.Data.done() );
        })) {

            List<String> result = new ArrayList<>();

            it.forEachAsync(result::add).thenAccept((t) -> {
                System.out.println("Finished");
            }).join();


            assertEquals(10, result.size());
            assertIterableEquals(List.of("e0", "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9"), result);
        }
    }


    @Test
    public void asyncQueueToStreamTest() throws Exception {

        // AsyncQueue initialized with a direct executor. No thread is used on next() invocation
        try(final var it = AsyncGeneratorFlow.<String>create(d -> {
            for( int i = 0 ; i < 10 ; ++i ) {
                d.dispatchAsync( AsyncGenerator.Data.of( completedFuture("e"+i )) );
            }
            d.dispatchAsync( AsyncGenerator.Data.done() );
        })) {

            java.util.stream.Stream<String> result = it.stream();

            java.util.Optional<String> lastElement = result.reduce((a, b) -> b);

            assertTrue(lastElement.isPresent());
            assertEquals("e9", lastElement.get());
        }
    }

    @Test
    public void asyncQueueIteratorExceptionTest() throws Exception {

        try(final var it = AsyncGeneratorFlow.<String>create( d -> {
            for( int i = 0 ; i < 10 ; ++i ) {
                d.dispatchAsync( AsyncGenerator.Data.of( completedFuture("e"+i )) );

                if( i == 2 ) {
                    final var ex = new RuntimeException("error test");
                    d.dispatchAsync( AsyncGenerator.Data.error(ex) );
                    throw ex;
                }
            }
            d.dispatchAsync( AsyncGenerator.Data.done() );
        })) {

            java.util.stream.Stream<String> result = it.stream();

            final var ex = assertThrows(Exception.class, () -> result.reduce((a, b) -> b));

            assertEquals("error test", ExceptionUtils.getRootCause(ex).getMessage());
        }

    }

    @Test
    public void asyncQueueForEachExceptionTest() throws Exception {

        try(final var it = AsyncGeneratorFlow.<String>create(d -> {
            for( int i = 0 ; i < 10 ; ++i ) {
                d.dispatchAsync( AsyncGenerator.Data.of( completedFuture("e"+i )) );

                if( i == 2 ) {
                    final var ex = new RuntimeException("error test");
                    d.dispatchAsync( AsyncGenerator.Data.error(ex) );
                    throw ex;
                }
            }
            d.dispatchAsync( AsyncGenerator.Data.done() );
        })) {

            final var ex = assertThrows(Exception.class, () -> it.forEachAsync(System.out::println).get());

            assertEquals("error test", ExceptionUtils.getRootCause(ex).getMessage());
        }

    }

}
