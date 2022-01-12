# Examples for streaming HTTP multipart data

Streaming data without any intermediate file storage does not work out of the box.
This repository gives an example of how to achieve it with Spring Boot
and also offers some example clients with different frameworks.

Server implementation: [UploadController.java](./src/main/java/de/qaware/multipart/UploadController.java)

Client implementation: [UploadClient.java](./client/src/main/java/de/qaware/multipart/client/UploadClient.java)

## Usage

```shell
# Start server
./gradlew bootRun
# Run client (--args is optional, parameter values must match enum values)
./gradlew :client:run --args="--client-type APACHE_HTTP5 --request-type MULTIPART_FILE"
```
