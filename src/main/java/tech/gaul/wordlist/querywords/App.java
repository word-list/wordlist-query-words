package tech.gaul.wordlist.querywords;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;

public class App implements RequestHandler<SQSEvent, Object> {
    @Override
    public Object handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            String body = message.getBody();
            System.out.println("Received message: " + body);
            // Process the message here
        }
        return null;
    }
}