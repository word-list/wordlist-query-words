package tech.gaul.wordlist.queryword.models;

import java.util.Date;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Builder
@Getter
@Setter
public class ActiveWordQuery {
    
    private String id;

    private String word;    
    private String batchRequestId;
    
    private Date createdAt;
    private Date updatedAt;

    private String status;

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }
}
