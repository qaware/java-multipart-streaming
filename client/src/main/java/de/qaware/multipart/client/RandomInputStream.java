package de.qaware.multipart.client;

import java.io.InputStream;
import java.util.Random;

public class RandomInputStream extends InputStream {
    private final Random random;
    private final long size;
    private final long blockSize;
    private long currentBlockSize;
    private int lastUsedByte;
    private long index;

    public RandomInputStream(long size, long blockSize, long seed) {
        if (blockSize < 1) {
            throw new IllegalArgumentException("Block size must be at least one byte!");
        }

        this.size = size;
        this.blockSize = blockSize;
        this.currentBlockSize = blockSize;
        this.random = new Random(seed);
        this.lastUsedByte = random.nextInt(255);
    }

    @Override
    public int read() {
        if (index == size) {
            return -1;
        }

        if (index == currentBlockSize) {
            lastUsedByte = random.nextInt(255);
            currentBlockSize += blockSize;
        }

        index++;

        return lastUsedByte;
    }
}
