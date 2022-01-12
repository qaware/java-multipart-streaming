package de.qaware.multipart.common;

import java.io.InputStream;
import java.util.Arrays;

public class FastInputStream extends InputStream {
    private final long size;
    private long index;

    public FastInputStream(long size) {
        this.size = size;
    }

    @Override
    public int read() {
        if (index == size) {
            return -1;
        }

        index++;
        return 170;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        int numBytes = Math.min(len, (int) (size - index));
        int upper = off + numBytes;
        Arrays.fill(b, off, upper, (byte) 170);
        index += numBytes;
        if (numBytes == 0) {
            return -1;
        }
        return numBytes;
    }
}
