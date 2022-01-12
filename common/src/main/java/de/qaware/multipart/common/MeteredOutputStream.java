package de.qaware.multipart.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public class MeteredOutputStream extends OutputStream {
    private final OutputStream outputStream;
    @Getter
    private final Map<Long, Long> map = new LinkedHashMap<>();

    private long numBytes = 0;

    @Override
    public void write(int b) throws IOException {
        numBytes++;
        long now = System.currentTimeMillis();
        long key = now / 10_000_000;
        map.put(key, numBytes);
        outputStream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        long before = System.currentTimeMillis();
        outputStream.write(b, off, len);
        numBytes += len;
        long after = System.currentTimeMillis();
        long now = (before + after) / 2;
        long key = now / 10_000_000;
        map.put(key, numBytes);
    }

    public void toCsv(OutputStream outputStream) throws IOException {
        for (var entry : map.entrySet()) {
            outputStream.write(((entry.getKey() * 10) + ";" + entry.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
