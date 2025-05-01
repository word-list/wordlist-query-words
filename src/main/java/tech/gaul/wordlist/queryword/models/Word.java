package tech.gaul.wordlist.queryword.models;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class Word {
    
    private String word;
    private String type;
    private int offensiveness;
    private int commonness;
    private int sentiment;

}
