package de.qaware.multipart.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public class MeteredInputStream extends InputStream {
    private final InputStream inputStream;
    @Getter
    private final Map<Long, Long> map = new LinkedHashMap<>();

    private long numBytes = 0;

    @Override
    public int read() throws IOException {
        numBytes++;
        long now = System.currentTimeMillis();
        long key = now / 10_000_000;
        map.put(key, numBytes);
        return inputStream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        long before = System.currentTimeMillis();
        int n = inputStream.read(b, off, len);
        numBytes += n;
        long after = System.currentTimeMillis();
        long now = (before + after) / 2;
        long key = now / 10;
        map.put(key, numBytes);
        return n;
    }

    public void toCsv(Path path) throws IOException {
        try (var fos = new FileOutputStream(path.toFile())) {
            toCsv(fos);
        }
    }

    public void toCsv(OutputStream outputStream) throws IOException {
        for (var entry : map.entrySet()) {
            outputStream.write(((entry.getKey() * 10) + " " + entry.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
