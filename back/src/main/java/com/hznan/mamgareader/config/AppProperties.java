package com.hznan.mamgareader.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Storage storage = new Storage();
    private Translator translator = new Translator();
    private Ollama ollama = new Ollama();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs = 86400000;
    }

    @Data
    public static class Storage {
        private String basePath = "./manga-data";
    }

    @Data
    public static class Translator {
        private String apiBase = "http://localhost:5003";
    }

    @Data
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
    }

    @Data
    public static class RateLimit {
        private int capacity = 10;
        private int tokensPerSecond = 2;
    }
}
