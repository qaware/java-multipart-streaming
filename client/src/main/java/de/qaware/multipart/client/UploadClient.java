package de.qaware.multipart.client;

import okhttp3.*;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.EntityBuilder;
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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;

@Command(name = "upload", mixinStandardHelpOptions = true)
public class UploadClient implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(UploadClient.class);

    private static final long SEED = 42;
    private static final String BASE_URL = "http://localhost:8080";

    @Option(names = {"--client-type"})
    ClientType clientType = ClientType.APACHE_HTTP5;
    @Option(names = {"--request-type"})
    RequestType requestType = RequestType.MULTIPART;
    @Option(names = {"--random-data"})
    boolean randomData = false;
    @Option(names = {"--num-bytes"})
    long numBytes = 2 * 1024L * 1024L * 1024L;

    @Override
    public Integer call() throws Exception {
        String url = BASE_URL + requestType.getPath();
        InputStream inputStream = randomData ? new RandomInputStream(numBytes, 32, SEED) : new FastInputStream(numBytes);

        log.info("Performing {} request with client {} against {} using {}",
                requestType, clientType, url, inputStream.getClass().getSimpleName());

        long tStart = System.nanoTime();
        Checksum checksum = new CRC32();
        try (CheckedInputStream checkedInputStream = new CheckedInputStream(inputStream, checksum)) {
            ServerResponse response = switch (requestType) {
                case MULTIPART, MULTIPART_FILE -> switch (clientType) {
                    case APACHE_HTTP5 -> multipartApacheHttpClient5(url, checkedInputStream);
                    case OK_HTTP -> okHttpClient(url, checkedInputStream);
                    case SPRING_WEB_FLUX -> multipartSpringBootWebClient(url, checkedInputStream);
                };
                case SINGLE_PART -> switch (clientType) {
                    case APACHE_HTTP5 -> singlePartApacheHttpClient5(url, checkedInputStream);
                    case SPRING_WEB_FLUX -> singlePartSpringBootWebClient(url, checkedInputStream);
                    default -> throw new IllegalArgumentException("Client type not supported");
                };
            };
            log.info("Response: {} - {}", response.code, response.body);
        }
        long tEnd = System.nanoTime();
        double duration = (tEnd - tStart) / 1e9;
        String mbPerSecond = String.format("%.3f", numBytes / duration / 1024 / 1024);
        log.info("Number of bytes: {}", numBytes);
        log.info("Checksum: {}", checksum.getValue());
        log.info("Duration: {}", duration);
        log.info("MB/s: {}", mbPerSecond);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new UploadClient()).execute(args);
        System.exit(exitCode);
    }


    private static ServerResponse multipartApacheHttpClient5(String url, InputStream inputStream) throws IOException {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httppost = new HttpPost(url);

            HttpEntity httpEntity = MultipartEntityBuilder.create()
                    .addBinaryBody("file", inputStream, ContentType.APPLICATION_OCTET_STREAM, null)
                    .build();
            httppost.setEntity(httpEntity);

            try (final CloseableHttpResponse response = httpclient.execute(httppost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                return new ServerResponse(response.getCode(), responseBody);
            } catch (ParseException e) {
                throw new IOException("Can not parse response", e);
            }
        }
    }

    private static ServerResponse singlePartApacheHttpClient5(String url, InputStream inputStream) throws IOException {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httppost = new HttpPost(url);

            httppost.setEntity(EntityBuilder.create()
                    .setStream(inputStream)
                    .setContentType(ContentType.APPLICATION_OCTET_STREAM)
                    .build());

            try (final CloseableHttpResponse response = httpclient.execute(httppost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                return new ServerResponse(response.getCode(), responseBody);
            } catch (ParseException e) {
                throw new IOException("Can not parse response", e);
            }
        }
    }

    private static ServerResponse okHttpClient(String url, InputStream inputStream) throws IOException {
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
                .url(url)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            var responseBody = response.body();
            return new ServerResponse(response.code(), responseBody == null ? "" : responseBody.string());
        }
    }

    private static ServerResponse multipartSpringBootWebClient(String url, InputStream inputStream) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file1", new InputStreamResource(inputStream), MediaType.APPLICATION_OCTET_STREAM);

        WebClient client = WebClient.create(url);
        ResponseEntity<String> response = client.post()
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .toEntity(String.class)
                .block();
        if (response != null) {
            return new ServerResponse(response.getStatusCodeValue(), response.getBody());
        }
        return new ServerResponse(0, "");
    }

    private static ServerResponse singlePartSpringBootWebClient(String url, InputStream inputStream) {
        WebClient client = WebClient.create(url);
        ResponseEntity<String> response = client.post()
                .body(BodyInserters.fromResource(new InputStreamResource(inputStream)))
                .retrieve()
                .toEntity(String.class)
                .block();
        if (response != null) {
            return new ServerResponse(response.getStatusCodeValue(), response.getBody());
        }
        return new ServerResponse(0, "");
    }

    private record ServerResponse(int code, String body) {
    }
}


