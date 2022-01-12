package de.qaware.multipart;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.common.StorageSharedKeyCredential;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class BlobStorageAzure {

    private final BlobContainerClient blobContainerClient = createContainerClient();

    private BlobContainerClient createContainerClient() {
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(
                "devstoreaccount1",
                "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=="
        );
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint("http://localhost:10000/devstoreaccount1")
                .credential(credential)
                .buildClient();
        return blobServiceClient.getBlobContainerClient("container1");
    }

    public void storeDocument(UUID id, InputStream inputStream) {
        var parallelTransferOptions = new ParallelTransferOptions()
                .setBlockSizeLong(100 * 1024 * 1024L)
                .setMaxConcurrency(1)
                .setMaxSingleUploadSizeLong(2 * 1024 * 1024L);
        var blobClient = getContainerClient().getBlobClient(id.toString());
        var blobParallelUploadOptions = new BlobParallelUploadOptions(inputStream)
                .setParallelTransferOptions(parallelTransferOptions);
        var blockBlobItemResponse = blobClient.uploadWithResponse(
                blobParallelUploadOptions,
                Duration.ofSeconds(60),
                Context.NONE
        );
        log.info("Upload response: {}", blockBlobItemResponse);
    }

    public void readDocument(UUID id, OutputStream outputStream) {
        BlobClient blobClient = getContainerClient().getBlobClient(id.toString());
        blobClient.downloadStream(outputStream);
    }

    private BlobContainerClient getContainerClient() {
        if (!blobContainerClient.exists()) {
            blobContainerClient.create();
        }
        return blobContainerClient;
    }
}
