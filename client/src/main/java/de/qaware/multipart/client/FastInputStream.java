package de.qaware.multipart.client;

import java.io.InputStream;

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
}
