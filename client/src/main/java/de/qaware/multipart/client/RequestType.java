package de.qaware.multipart.client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum RequestType {
    MULTIPART("/api/multipart"),
    MULTIPART_FILE("/api/multipart/file"),
    SINGLE_PART("/api/singlepart"),
    ;
    private final String path;
}
