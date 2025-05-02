package tech.gaul.wordlist.queryword;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.batches.Batch;
import com.openai.models.batches.BatchCreateParams;
import com.openai.models.batches.BatchCreateParams.CompletionWindow;
import com.openai.models.batches.BatchCreateParams.Endpoint;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;

import lombok.Builder;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import tech.gaul.wordlist.queryword.models.ActiveWordQuery;
import tech.gaul.wordlist.queryword.models.BatchRequest;

@Builder
public class WordQuerier {

        private final String INSTRUCTIONS = """
                        Your goal is to determine scores for specific attributes of words in the English language.

                        You will be provided with a single English word, and you will output a json object containing the following information:

                        {
                            word: string // the word requested
                            types: string[] // the valid parts of speech of the word, e.g. "noun", "verb", "adjective", etc.
                            offensiveness: int // a score from 0 to 5, where 0 is not offensive at all and 5 is extremely offensive
                            commonness: int // a score from 0 to 5, where 0 is not common at all and 5 is extremely common
                            sentiment: int // a score from -5 to 5, where -5 is extremely negative, 5 is extremely positive and 0 is neutral
                            note: string // any note or error related to scoring the word (see instructions)
                        }

                        If you are unsure about the word, please respond with "unknown" in the note field and leave the score fields blank.
                        If you recognise the word as a non-English word, please respond with "non-english" and the language you recognised the word from in the note field but still score the word as if it were an English word if possible.

                        Words can have many types, but for this task you must keep it simple and use only the most common types.  When a word has multiple types, please include all of them in the types field.
                        """;

        @Builder.Default
        private String model = "gpt-4.1-mini-2025-04-14"; // gpt-4.1-nano-2025-04-14

        private BatchRequest createWordQuery(String word) {

                String requestId = UUID.randomUUID().toString().replace("-", "");

                return BatchRequest.builder()
                                .customId(requestId)
                                .method("POST")
                                .url("/v1/responses")
                                .body(BatchRequest.Body.builder()
                                                .model(model)
                                                .responseFormat(BatchRequest.ResponseFormat.builder()
                                                                .type("json_object")
                                                                .build())
                                                .requests(new BatchRequest.Request[] {
                                                                BatchRequest.Request.builder()
                                                                                .instructions(INSTRUCTIONS)
                                                                                .input(word)
                                                                                .build()
                                                })
                                                .build())
                                .build();
        }

        public ActiveWordQuery[] createWordQueries(String[] words) {

                // Build JSONL file containing all requests. This is the batch.
                StringBuilder jsonlBuilder = new StringBuilder();
                ObjectMapper objectMapper = new ObjectMapper();
                DynamoDbEnhancedClient dynamoDbClient = DependencyFactory.dynamoDbClient();
                TableSchema<ActiveWordQuery> wordQueryTableSchema = TableSchema.fromBean(ActiveWordQuery.class);

                List<ActiveWordQuery> wordQueries = new ArrayList<>();

                for (String word : words) {
                        try {
                                BatchRequest request = createWordQuery(word);
                                jsonlBuilder.append(objectMapper.writeValueAsString(request)).append("\n");

                                // Create a WordQuery object and save it to DynamoDB.
                                ActiveWordQuery wordQuery = ActiveWordQuery.builder()
                                                .id(UUID.randomUUID().toString())
                                                .word(word)
                                                .batchRequestCustomId(request.getCustomId())
                                                .batchRequestId(null) // Will be set after the batch is created
                                                .uploadedFileId(null) // Will be set after the file is uploaded
                                                .createdAt(new Date())
                                                .updatedAt(new Date())
                                                .status("Initialising")
                                                .build();
                                wordQueries.add(wordQuery);

                                dynamoDbClient.table("WordQueries", wordQueryTableSchema).putItem(wordQuery);

                        } catch (JsonProcessingException e) {
                                e.printStackTrace();
                        }
                }

                byte[] bytes = jsonlBuilder.toString().getBytes();

                // Upload the file to OpenAI.
                FileCreateParams fileParams = FileCreateParams.builder()
                                .purpose(FilePurpose.BATCH)
                                .file(bytes)
                                .build();

                OpenAIClient openAIClient = DependencyFactory.getOpenAIClient();
                FileObject file = openAIClient.files().create(fileParams);

                // Update the WordQuery objects in DynamoDB with the uploaded file ID.
                wordQueries.stream()
                                .map(wordQuery -> {
                                        wordQuery.setUploadedFileId(file.id());
                                        wordQuery.setStatus("Querying");
                                        wordQuery.setUpdatedAt(new Date());
                                        return WriteBatch.builder(ActiveWordQuery.class)
                                                        .mappedTableResource(dynamoDbClient.table("active-queries",
                                                                        wordQueryTableSchema))
                                                        .addPutItem(wordQuery)
                                                        .build();
                                })
                                .forEach(updateFileIdBatch -> dynamoDbClient
                                                .batchWriteItem(b -> b.writeBatches(updateFileIdBatch)));

                // Create the batch request using the uploaded file.
                BatchCreateParams batchParams = BatchCreateParams.builder()
                                .completionWindow(CompletionWindow._24H)
                                .endpoint(Endpoint.V1_RESPONSES)
                                .inputFileId(file.id())
                                .build();

                Batch batch = openAIClient.batches().create(batchParams);

                // Update the WordQuery objects in DynamoDB with the batch ID.
                wordQueries.stream()
                                .map(wordQuery -> {
                                        wordQuery.setBatchRequestId(batch.id());
                                        wordQuery.setStatus("Awaiting Response");
                                        wordQuery.setUpdatedAt(new Date());
                                        return WriteBatch.builder(ActiveWordQuery.class)
                                                        .mappedTableResource(dynamoDbClient.table("WordQueries",
                                                                        wordQueryTableSchema))
                                                        .addPutItem(wordQuery)
                                                        .build();
                                })
                                .forEach(updateBatchIdBatch -> dynamoDbClient
                                                .batchWriteItem(b -> b.writeBatches(updateBatchIdBatch)));

                // DB records will now be polled by another function to check for responses and
                // process them.

                return wordQueries.toArray(new ActiveWordQuery[0]);
        }

}
