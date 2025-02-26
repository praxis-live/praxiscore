package org.praxislive.code.userapi;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.praxislive.core.Lookup;

import static org.junit.jupiter.api.Assertions.*;

public class DataTest {

    @Test
    public void testBasicIntPipeline() {
        IntBufferDataSink sink = new IntBufferDataSink();
        int[] result;
        result = sink.process(new int[]{42});
        assertEquals(0, result[0]);
        Data.Pipe<int[]> src1 = Data.with(b -> b[0] = 10);
        Data.Pipe<int[]> src2 = Data.with(b -> b[0] = 20);
        Data.Pipe<int[]> identity = Data.identity();
        Data.link(src1, identity);
        Data.link(src2, identity);
        Data.link(identity, sink.input());
        result = sink.process(new int[]{42});
        assertEquals(30, result[0]);
        result = sink.process(new int[]{42, 42});
        assertEquals(30, result[0]);
        assertEquals(0, result[1]);
    }

    @Test
    public void testBasicIntPipelineFluid() {
        IntBufferDataSink sink = new IntBufferDataSink();
        int[] result;
        result = sink.process(new int[]{42});
        assertEquals(0, result[0]);
        Data.<int[]>identity().withSources(
                Data.with(b -> b[0] = 10),
                Data.with(b -> b[0] = 20)
        ).linkTo(sink.input());
        result = sink.process(new int[]{42});
        assertEquals(30, result[0]);
        result = sink.process(new int[]{42, 42});
        assertEquals(30, result[0]);
        assertEquals(0, result[1]);

        sink.input().disconnectSources();
        Data.Pipe<int[]> in = Data.identity();
        in.linkTo(
                Data.with(b -> b[0] = 5),
                Data.with(b -> b[1] = 10),
                Data.with(b -> b[0] *= b[1]),
                sink.input()
        );
        result = sink.process(new int[]{42, 42});
        assertEquals(50, result[0]);
        assertEquals(10, result[1]);
    }

    @Test
    public void testCombinePipeline() {
        IntBufferDataSink sink = new IntBufferDataSink();
        int[] result;

        Data.<int[]>combine((dst, srcs) -> {
            assertEquals(0, dst[0]);
            assertTrue(srcs.isEmpty());
            return new int[]{42};
        }).linkTo(sink.input());

        result = sink.process(new int[]{21});
        assertEquals(42, result[0]);

        sink.input().disconnectSources();

        Data.<int[]>combine((dst, srcs) -> {
            assertEquals(2, dst[0]);
            assertEquals(2, srcs.size());
            dst[0] = srcs.get(0)[0] * srcs.get(1)[0];
            return dst;
        }).withSources(
                Data.with(b -> b[0] = 2),
                Data.with(b -> b[0] = 4),
                Data.with(b -> b[0] = 8))
                .linkTo(sink.input());
        result = sink.process(new int[]{21});
        assertEquals(4 * 8, result[0]);
    }
    
    @Test
    public void testCombineWithPipeline() {
        IntBufferDataSink sink = new IntBufferDataSink();
        int[] result;

        Data.<int[]>combineWith((dst, srcs) -> {
            assertEquals(2, dst[0]);
            assertEquals(2, srcs.size());
            dst[0] = srcs.get(0)[0] * srcs.get(1)[0];
        }).withSources(
                Data.with(b -> b[0] = 2),
                Data.with(b -> b[0] = 4),
                Data.with(b -> b[0] = 8))
                .linkTo(sink.input());
        result = sink.process(new int[]{21});
        assertEquals(4 * 8, result[0]);
    }

    @Test
    public void testImmutablePipeline() {
        Data.Sink<String> sink = new TestDataSink<>();
        Data.Pipe<String> identity = Data.<String>identity().linkTo(sink.input());
        String result;
        result = sink.process("FOO");
        assertEquals("FOO", result);
        sink.onClear(s -> "");
        result = sink.process("FOO");
        assertEquals("", result);
        sink.onAccumulate((dst, src) -> dst + src);
        Data.supply(() -> "BAR").linkTo(identity);
        result = sink.process("FOO");
        assertEquals("BAR", result);
        Data.supply(() -> "BAZ").linkTo(identity);
        result = sink.process("FOO");
        assertEquals("BARBAZ", result);
    }

    @Test
    public void testMultipleSinksNoSource() {
        IntBufferDataSink sink = new IntBufferDataSink();
        int[] result;
        // add to existing value
        Data.<int[]>with(b -> b[0] += 21)
                .linkTo(Data.identity(), sink.input())
                .linkTo(Data.identity(), sink.input());
        result = sink.process(new int[1]);
        assertEquals(42, result[0]);
        // run again and check cleared
        result = sink.process(new int[1]);
        assertEquals(42, result[0]);
    }

    private static class TestDataSink<T> extends Data.Sink<T> {

        @Override
        protected void log(Exception ex) {
            throw new RuntimeException(ex);
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }

    }

    private static class IntBufferDataSink extends TestDataSink<int[]> {

        private int activeBufferCount;

        private IntBufferDataSink() {
            onCreate(b -> {
                activeBufferCount++;
                return new int[b.length];
            });
            onClear(b -> {
                Arrays.fill(b, 0);
                return b;
            });
            onAccumulate((dst, src) -> {
                int len = Math.min(dst.length, src.length);
                for (int i = 0; i < len; i++) {
                    dst[i] += src[i];
                }
                return dst;
            });
            onValidate((dst, src) -> {
                return dst.length == src.length;
            });
            onDispose(s -> {
                activeBufferCount--;
            });
        }

    }

}
