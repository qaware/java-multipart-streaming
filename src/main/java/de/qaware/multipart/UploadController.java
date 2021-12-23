package de.qaware.multipart;

import org.apache.tomcat.util.http.fileupload.MultipartStream;
import org.apache.tomcat.util.http.fileupload.ParameterParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;

@RestController
@RequestMapping("/api/multipart")
public class UploadController {
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
            long numBytes = readBodyData(multipartStream, checksum);
            uploadResults.add(new UploadResult(numBytes, checksum.getValue()));
        } while (multipartStream.readBoundary());
        return ResponseEntity.ok(uploadResults);
    }

    private long readBodyData(MultipartStream multipartStream, Checksum checksum) throws IOException {
        long numBytes;
        try (OutputStream outputStream = OutputStream.nullOutputStream();
                CheckedOutputStream checkedOutputStream = new CheckedOutputStream(outputStream, checksum)) {
            numBytes = multipartStream.readBodyData(checkedOutputStream);
        }
        return numBytes;
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
        byte[] boundary;
        boundary = boundaryStr.getBytes(StandardCharsets.ISO_8859_1);
        return boundary;
    }
}
