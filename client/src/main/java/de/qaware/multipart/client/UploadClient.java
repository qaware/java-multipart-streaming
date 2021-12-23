package de.qaware.multipart.client;

import okhttp3.*;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;

public class UploadClient {
    private static final long SEED = 42;
    private static final long NUM_BYTES = 1024L * 1024L * 1024L;
    private static final Logger log = LoggerFactory.getLogger(UploadClient.class);

    private static final String URL = "http://localhost:8080/api/multipart";

    public static void main(String[] args) throws IOException, ParseException {

        HttpClient httpClient = HttpClient.APACHE_HTTP5;
        if (args.length > 1) {
            httpClient = HttpClient.valueOf(args[0]);
        }

        long tStart = System.nanoTime();
        Checksum checksum = new CRC32();
        try (InputStream inputStream = new RandomInputStream(NUM_BYTES, 32, SEED);
             CheckedInputStream checkedInputStream = new CheckedInputStream(inputStream, checksum)) {
            switch (httpClient) {
                case APACHE_HTTP5 -> apacheHttpClient5(checkedInputStream);
                case OK_HTTP -> okHttpClient(checkedInputStream);
                case SPRING_WEB_FLUX -> springBootWebClient(checkedInputStream);
            }
        }
        long tEnd = System.nanoTime();
        double duration = (tEnd - tStart) / 1e9;
        log.info("Number of bytes: {}", NUM_BYTES);
        log.info("Checksum: {}", checksum.getValue());
        log.info("Duration: {}", duration);
    }

    private static void apacheHttpClient5(InputStream inputStream)
            throws IOException, ParseException {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httppost = new HttpPost(URL);

            HttpEntity httpEntity = MultipartEntityBuilder.create()
                    .addBinaryBody("file", inputStream, ContentType.APPLICATION_OCTET_STREAM, null)
                    .build();
            httppost.setEntity(httpEntity);

            try (final CloseableHttpResponse response = httpclient.execute(httppost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                log.info("Response: {} {}\n\n{}",
                        response.getCode(),
                        response.getReasonPhrase(),
                        responseBody
                );
            }
        }
    }

    private static void okHttpClient(InputStream inputStream) throws IOException {
        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = new RequestBody() {
            @Override
            public okhttp3.MediaType contentType() {
                return okhttp3.MediaType.get("application/octet-stream");
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                try (Source source = Okio.source(inputStream)) {
                    sink.writeAll(source);
                }
            }
        };

        RequestBody body = new MultipartBody.Builder()
                .setType(okhttp3.MediaType.get("multipart/form-data"))
                .addFormDataPart(
                        "file1",
                        "",
                        requestBody
                )
                .build();
        Request request = new Request.Builder()
                .url(URL)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            log.info("Response: {}", response.code());
        }
    }

    private static void springBootWebClient(InputStream inputStream) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file1", new InputStreamResource(inputStream), MediaType.APPLICATION_OCTET_STREAM);

        WebClient client = WebClient.create(URL);
        ResponseEntity<String> response = client.post()
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .toEntity(String.class)
                .block();
        if (response != null) {
            log.info("Response: {}", response.getStatusCode());
        } else {
            log.error("No response");
        }
    }
}


