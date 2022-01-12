package de.qaware.multipart;

import de.qaware.multipart.common.MeteredInputStream;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.MultipartStream;
import org.apache.tomcat.util.http.fileupload.ParameterParser;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UploadController {
    private static final int BUFFER_SIZE = 32 * 1024;
    @Setter
    private ConsumeType consumeType = ConsumeType.NULL;
    private final BlobStorageAzure blobStorageAzure;

    @PostMapping(value = "multipart", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<UploadResult>> storeDocument(MultipartHttpServletRequest request) throws IOException {
        MultipartStream multipartStream = createMultipartStream(request);
        boolean hasData = multipartStream.skipPreamble();
        if (!hasData) {
            throw new IOException("No data to read from multipart stream");
        }

        List<UploadResult> uploadResults = new ArrayList<>();
        do {
            multipartStream.readHeaders();
            Checksum checksum = new CRC32();
            long numBytes = consumeData(multipartStream.newInputStream(), checksum);
            uploadResults.add(new UploadResult(numBytes, checksum.getValue()));
        } while (multipartStream.readBoundary());
        return ResponseEntity.ok(uploadResults);
    }

    @PostMapping(value = "multipart/file", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<UploadResult>> storeDocument(@RequestPart(name = "file1") InputStreamResource multipartFile) throws IOException {
        Checksum checksum = new CRC32();
        long numBytes = consumeData(multipartFile.getInputStream(), checksum);
        return ResponseEntity.ok(List.of(new UploadResult(numBytes, checksum.getValue())));
    }

    @PostMapping(value = "singlepart", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<UploadResult> upload(@RequestBody InputStreamResource inputStream) throws IOException {
        Checksum checksum = new CRC32();
        long numBytes = consumeData(inputStream.getInputStream(), checksum);
        return ResponseEntity.ok(new UploadResult(numBytes, checksum.getValue()));
    }

    private long consumeData(InputStream inputStream, Checksum checksum) throws IOException {
        MeteredInputStream meteredInputStream = new MeteredInputStream(inputStream);
        long numBytes = switch (consumeType) {
            case NULL -> readBodyData(inputStream, checksum);
            case BLOB -> storeInBlob(meteredInputStream);
        };
        meteredInputStream.toCsv(Path.of("stats-server.csv"));
        return numBytes;
    }

    private long readBodyData(InputStream inputStream, Checksum checksum) throws IOException {
        try (OutputStream outputStream = OutputStream.nullOutputStream();
             CheckedOutputStream checkedOutputStream = new CheckedOutputStream(outputStream, checksum)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            return Streams.copy(inputStream, checkedOutputStream, false, buffer);
        }
    }

    private long storeInBlob(InputStream inputStream) {
        MeteredInputStream meteredInputStream = new MeteredInputStream(inputStream);
        blobStorageAzure.storeDocument(UUID.randomUUID(), meteredInputStream);
        return 0;
    }


    private MultipartStream createMultipartStream(MultipartHttpServletRequest request) throws IOException {
        return new MultipartStream(
                request.getInputStream(),
                getBoundary(request.getContentType()),
                null
        );
    }

    private byte[] getBoundary(String contentType) {
        ParameterParser parser = new ParameterParser();
        parser.setLowerCaseNames(true);
        Map<String, String> params = parser.parse(contentType, new char[]{';', ','});
        String boundaryStr = params.get("boundary");

        if (boundaryStr == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No boundary definition found in Content-Type header: " + contentType
            );
        }
        return boundaryStr.getBytes(StandardCharsets.ISO_8859_1);
    }
}
