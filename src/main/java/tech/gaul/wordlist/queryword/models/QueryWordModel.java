package tech.gaul.wordlist.queryword.models;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class QueryWordModel {

    private String word;
    private boolean force;
    
}
