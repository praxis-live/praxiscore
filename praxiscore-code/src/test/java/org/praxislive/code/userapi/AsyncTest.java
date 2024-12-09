package org.praxislive.code.userapi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.praxislive.core.types.PError;

import static org.junit.jupiter.api.Assertions.*;
import org.praxislive.core.Call;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Value;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PReference;
import org.praxislive.core.types.PString;

/**
 *
 */
public class AsyncTest {

    public AsyncTest() {

    }

    @Test
    public void testComplete() {
        Async<Integer> async = new Async<>();
        assertFalse(async.done());
        assertTrue(async.complete(1));
        assertFalse(async.complete(2));
        assertTrue(async.done());
        assertFalse(async.failed());
        assertEquals(1, async.result());
        assertNull(async.error());
    }

    @Test
    public void testError() {
        Async<Integer> async = new Async<>();
        PError err1 = PError.of("ERROR1");
        PError err2 = PError.of("Error2");
        assertFalse(async.done());
        assertTrue(async.fail(err1));
        assertTrue(async.done());
        assertTrue(async.failed());
        assertFalse(async.fail(err2));
        assertEquals(err1, async.error());
        assertNull(async.result());
    }

    @Test
    public void testBind() {
        Async<String> source = new Async<>();
        Async<Object> target = new Async<>();
        Async.bind(source, target);
        source.complete("FOO");
        assertTrue(target.done());
        assertEquals("FOO", target.result());

        source = new Async<>();
        target = new Async<>();
        source.complete("BAR");
        Async.bind(source, target);
        assertTrue(target.done());
        assertEquals("BAR", target.result());

        source = new Async<>();
        target = new Async<>();
        Async.bind(source, target);
        PError error = PError.of("ERROR");
        source.fail(error);
        assertTrue(target.done());
        assertSame(error, target.error());
    }

    @Test
    public void testExtractArg() {
        // Test Value extract
        Async<Call> asyncCall = new Async<>();
        Async<Value> asyncValue = Async.extractArg(asyncCall, Value.class);
        asyncCall.complete(createCall().reply(Value.ofObject("TEST")));
        assertTrue(asyncValue.done());
        assertEquals(PString.of("TEST"), asyncValue.result());

        // Test coerced Value extract
        asyncCall = new Async<>();
        Async<PBoolean> asyncBoolean = Async.extractArg(asyncCall, PBoolean.class);
        asyncCall.complete(createCall().reply(Value.ofObject("true")));
        assertTrue(asyncBoolean.done());
        assertEquals(PBoolean.TRUE, asyncBoolean.result());

        // Test String extract
        asyncCall = new Async<>();
        Async<String> asyncMessage = Async.extractArg(asyncCall, String.class);
        asyncCall.complete(createCall().reply(Value.ofObject("TEST")));
        assertTrue(asyncMessage.done());
        assertEquals("TEST", asyncMessage.result());

        // Test String extract after precompletion
        asyncCall = new Async<>();
        asyncCall.complete(createCall().reply(Value.ofObject("TEST")));
        asyncMessage = Async.extractArg(asyncCall, String.class);
        assertTrue(asyncMessage.done());
        assertEquals("TEST", asyncMessage.result());

        // Test failed int extract
        asyncCall = new Async<>();
        Async<Integer> asyncInt = Async.extractArg(asyncCall, Integer.class);
        asyncCall.complete(createCall().reply(Value.ofObject("NOT A NUMBER")));
        assertTrue(asyncInt.done());
        assertTrue(asyncInt.failed());
        assertNotNull(asyncInt.error());

        // Test int extract from second arg
        asyncCall = new Async<>();
        asyncInt = Async.extractArg(asyncCall, Integer.class, 1);
        asyncCall.complete(createCall().reply(List.of(
                Value.ofObject("TEST"), Value.ofObject(42))));
        assertTrue(asyncInt.done());
        assertEquals(42, asyncInt.result());

        // Test Call error passed through
        asyncCall = new Async<>();
        PError err = PError.of("ERROR");
        asyncMessage = Async.extractArg(asyncCall, String.class);
        asyncCall.complete(createCall().error(err));
        assertTrue(asyncMessage.done());
        assertTrue(asyncMessage.failed());
        assertEquals(err, asyncMessage.error());

        // Test extract of custom reference
        record Foo(int value) {

        }
        asyncCall = new Async<>();
        Async<Foo> asyncFoo = Async.extractArg(asyncCall, Foo.class);
        asyncCall.complete(createCall().reply(PReference.of(new Foo(42))));
        assertTrue(asyncFoo.done());
        assertInstanceOf(Foo.class, asyncFoo.result());
        assertEquals(42, asyncFoo.result().value());

    }

    @Test
    public void testToCompletableFuture() {
        // completed after link
        Async<String> asyncString = new Async<>();
        CompletableFuture<String> futureString
                = Async.toCompletableFuture(asyncString)
                        .thenApply(s -> "Hello " + s);
        asyncString.complete("World");
        assertTrue(futureString.isDone());
        assertEquals("Hello World", futureString.resultNow());

        // completed before link
        asyncString = new Async<>();
        asyncString.complete("Universe");
        futureString
                = Async.toCompletableFuture(asyncString)
                        .thenApply(s -> "Hello " + s);
        assertTrue(futureString.isDone());
        assertEquals("Hello Universe", futureString.resultNow());

        // error after link
        asyncString = new Async<>();
        Exception ex = new Exception("FOO");
        futureString = Async.toCompletableFuture(asyncString);
        asyncString.fail(PError.of(ex));
        assertTrue(futureString.isCompletedExceptionally());
        assertSame(ex, futureString.exceptionNow());

        // error before link
        asyncString = new Async<>();
        ex = new Exception("FOO");
        asyncString.fail(PError.of(ex));
        futureString = Async.toCompletableFuture(asyncString);
        assertTrue(futureString.isCompletedExceptionally());
        assertSame(ex, futureString.exceptionNow());

        // test queued and future
        asyncString = new Async<>();
        futureString = Async.toCompletableFuture(asyncString);
        Async.Queue<String> queue = new Async.Queue<>();
        queue.add(asyncString);
        assertNull(queue.poll());
        asyncString.complete("QUEUED");
        assertSame(asyncString, queue.poll());
        assertTrue(futureString.isDone());
        assertEquals("QUEUED", futureString.resultNow());
    }

    @Test
    public void testQueuePoll() {
        Async<Integer> async1 = new Async<>();
        Async<Integer> async2 = new Async<>();
        Async<Integer> async3 = new Async<>();
        Async<Integer> async4 = new Async<>();

        Async.Queue<Integer> queue = new Async.Queue<>();
        queue.add(async1);
        queue.add(async2);
        queue.add(async3);
        queue.add(async4);

        assertEquals(4, queue.size());
        assertNull(queue.poll());
        async3.complete(3);
        assertEquals(async3, queue.poll());
        assertNull(queue.poll());
        assertEquals(3, queue.size());

        async4.fail(PError.of("FOUR"));
        async2.complete(2);
        async1.complete(1);

        assertEquals(async1, queue.poll());
        assertEquals(async2, queue.poll());
        assertEquals(async4, queue.poll());
        assertNull(queue.poll());

    }

    @Test
    public void testQueuePollSized() {
        Async<Integer> async1 = new Async<>();
        Async<Integer> async2 = new Async<>();
        Async<Integer> async3 = new Async<>();
        Async<Integer> async4 = new Async<>();

        Async.Queue<Integer> queue = new Async.Queue<>();
        queue.add(async1);
        queue.add(async2);
        queue.add(async3);

        async3.complete(3);

        var list = queue.limit(2);
        assertEquals(1, list.size());
        assertEquals(async1, list.get(0));
        assertEquals(2, queue.size());

        var ousted = queue.add(async4);
        assertEquals(async2, ousted);

        async4.fail(PError.of("FOUR"));

        assertEquals(async3, queue.poll());
        assertEquals(async4, queue.poll());
        assertNull(queue.poll());

    }

    @Test
    public void testQueueHandler() {
        Async<Integer> async1 = new Async<>();
        Async<Integer> async2 = new Async<>();
        Async<Integer> async3 = new Async<>();
        Async<Integer> async4 = new Async<>();

        Async.Queue<Integer> queue = new Async.Queue<>();

        List<Integer> results = new ArrayList<>();
        List<PError> errors = new ArrayList<>();

        queue.onDone(results::add, errors::add);

        async3.complete(3);
        queue.add(async1);
        queue.add(async2);
        queue.add(async3);
        queue.add(async4);

        assertEquals(1, results.size());
        assertEquals(3, results.get(0));

        async1.complete(1);
        async2.fail(PError.of("TWO"));
        assertEquals(2, results.size());
        assertEquals(1, results.get(1));
        assertEquals(1, errors.size());
        assertEquals("TWO", errors.get(0).message());

        queue.clear();
        async4.complete(4);
        assertEquals(2, results.size());
        assertEquals(1, errors.size());

    }

    private static Call createCall() {
        return Call.create(ControlAddress.of("/root/component.control"),
                ControlAddress.of("/test/component.control"),
                System.nanoTime());
    }

}
