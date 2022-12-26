package org.puffinbasic.runtime;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.puffinbasic.error.PuffinBasicRuntimeError;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.puffinbasic.error.PuffinBasicRuntimeError.ErrorCode.ILLEGAL_FUNCTION_PARAM;
import static org.puffinbasic.error.PuffinBasicRuntimeError.ErrorCode.IO_ERROR;

public class SoundState implements AutoCloseable {

    private static final class ClipState implements AutoCloseable {
        final AudioInputStream stream;
        final Clip clip;

        ClipState(AudioInputStream stream, Clip clip) {
            this.stream = stream;
            this.clip = clip;
        }

        @Override
        public void close() throws Exception {
            clip.close();
            stream.close();
        }
    }

    private final ExecutorService executor;
    private final AtomicInteger counter;
    private final Int2ObjectMap<ClipState> state;

    SoundState() {
        this.executor = Executors.newSingleThreadExecutor();
        this.counter = new AtomicInteger();
        this.state = new Int2ObjectOpenHashMap<>();
    }

    public int load(String file) {
        Future<Integer> future = executor.submit(() -> {
            File audioFile = new File(file);
            AudioInputStream audioStream;
            try {
                audioStream = AudioSystem.getAudioInputStream(audioFile);
            } catch (Exception e) {
                throw new PuffinBasicRuntimeError(
                        IO_ERROR,
                        "Failed to load audio file: " + file + ", error: " + e.getMessage()
                );
            }
            AudioFormat format = audioStream.getFormat();
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            Clip clip;
            try {
                clip = (Clip) AudioSystem.getLine(info);
                clip.open(audioStream);
            } catch (Exception e) {
                throw new PuffinBasicRuntimeError(
                        IO_ERROR,
                        "Failed to get/open line from audio: " + file + ", error: " + e.getMessage()
                );
            }
            int id = counter.incrementAndGet();
            state.put(id, new ClipState(audioStream, clip));
            return id;
        });
        try {
            return future.get();
        } catch (Exception e) {
            throw new PuffinBasicRuntimeError(
                    IO_ERROR,
                    "Failed to get id from loaded audio: " + file + ", error: " + e.getMessage()
            );
        }
    }

    private ClipState get(int id) {
        ClipState s = state.get(id);
        if (s == null) {
            throw new PuffinBasicRuntimeError(
                    ILLEGAL_FUNCTION_PARAM,
                    "Failed to get sound clip for id: " + id
            );
        }
        return s;
    }

    public void play(int id) {
        executor.submit(() -> {
            Clip clip = get(id).clip;
            if (clip.isRunning()) {
                clip.stop();
            }
            clip.setFramePosition(0);
            clip.start();
        });
    }

    public void stop(int id) {
        executor.submit(() -> {
            Clip clip = get(id).clip;
            if (clip.isRunning()) {
                clip.stop();
            }
        });
    }

    public void loop(int id) {
        executor.submit(() -> {
            Clip clip = get(id).clip;
            if (clip.isRunning()) {
                clip.stop();
            }
            clip.setFramePosition(0);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        });
    }

    @Override
    public void close() {
        state.values().forEach(s -> s.clip.close());
        executor.shutdownNow();
    }
}
