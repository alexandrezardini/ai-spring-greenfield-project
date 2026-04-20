package org.example.streamtubebackend;

import org.springframework.boot.SpringApplication;

public class TestStreamTubeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(StreamTubeBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
