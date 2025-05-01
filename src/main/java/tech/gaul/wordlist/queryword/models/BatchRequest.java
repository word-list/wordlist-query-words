package tech.gaul.wordlist.queryword.models;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class BatchRequest {
    
    private String customId;
    private String method = "POST";
    private String url = "/v1/responses";
    private Body body;

    @Builder
    @Setter
    @Getter
    public static class Body {
        private String model = "gpt-4.1-mini-2025-04-14"; // gpt-4.1-nano-2025-04-14

        @JsonAlias("response_format")
        private ResponseFormat responseFormat;

        private Request[] requests;
    }

    @Builder
    @Getter
    @Setter
    public static class ResponseFormat {
        private String type = "json_object";        
    }

    @Builder
    @Getter
    @Setter
    public static class Request {
        private String instructions;
        private String input;
    }
}
