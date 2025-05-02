package tech.gaul.wordlist.queryword;

import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.MappedTableResource;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import tech.gaul.wordlist.queryword.models.QueryWordModel;
import tech.gaul.wordlist.queryword.models.Word;

public class App implements RequestHandler<SQSEvent, Object> {

    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Object handleRequest(SQSEvent event, Context context) {

        TableSchema<Word> wordSchema = TableSchema.fromBean(Word.class);

        List<QueryWordModel> queries = event.getRecords().stream()
                .map(SQSEvent.SQSMessage::getBody)
                .map(body -> objectMapper.convertValue(body, QueryWordModel.class))
                .toList();

        if (queries.isEmpty()) {
            return null;
        }

        // Where isForce is true, always query, so get these words first.
        List<String> wordsToUpdate = queries.stream()
                .filter(QueryWordModel::isForce)
                .map(QueryWordModel::getWord)
                .toList();

        // For queries where isForce is false, check if the word is already in the
        // database; only add a word if it doesn't already exist.
        wordsToUpdate.addAll(getNonExistingWords(queries, wordSchema));

        WordQuerier wordQuerier = WordQuerier.builder()
                .model("gpt-4.1-mini-2025-04-14")
                .build();

        wordQuerier.createWordQueries(wordsToUpdate.toArray(new String[0]));
        

        return null;
    }

    private List<String> getNonExistingWords(List<QueryWordModel> queries, TableSchema<Word> wordSchema) {        
        List<String> checkWords = queries.stream()
                .filter(query -> !query.isForce())
                .map(QueryWordModel::getWord)
                .toList();

        DynamoDbEnhancedClient dbClient = DependencyFactory.dynamoDbClient();
        MappedTableResource<Word> wordTable = dbClient.table("words", wordSchema);

        ReadBatch.Builder<Word> readBatchBuilder = ReadBatch.builder(Word.class)
                .mappedTableResource(wordTable);

        for (String word : checkWords) {
            readBatchBuilder.addGetItem(b -> b.key(k -> k.partitionValue(word)));
        }

        ReadBatch readBatch = readBatchBuilder.build();

        BatchGetItemEnhancedRequest batchGetItemRequest = BatchGetItemEnhancedRequest.builder()
                .readBatches(readBatch)
                .build();

        List<String> existingWords = dbClient.batchGetItem(batchGetItemRequest).resultsForTable(wordTable).stream()
                .map(Word::getWord)
                .toList();

        checkWords.removeAll(existingWords);

        return checkWords;
    }
}