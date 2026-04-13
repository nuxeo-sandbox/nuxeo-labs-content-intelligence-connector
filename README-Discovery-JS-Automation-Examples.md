# nuxeo-labs-content-intelligence-connector: JavaScript Automation Examples for Knowledge Discovery 

These examples are referenced from the README of the plugin.

> [!IMPORTANT]
> All the examples assume Nuxeo was correctly configured to access Hyland CIC Knowledge Discovery service. See [README-Discovery](README-Discovery.md).

## Example Using `HylandKnowledgeDiscovery.Invoke`

In this example, we ask a question and pull the answer.

```javascript
/* Asks the question passed in the "question" parameter.
   if input is not null and is a list of Documents => assume they were
   previously sent to KD and their UUID = ObjectID in the repo

   The script expects 2 parameters, the question and the agentId to use (optional)

   agentId is optional. ID of the agent to use. Default value will be used by the plugin
   Still, we filter on kdinfo:isInKD

   Also, java.lang.Thread class has been allowed in an XML extension.
*/
function run(input, params) {
  
  var i, resultBlob, resultJsonBlob, resultJsonStr, resultJson, objKeys,
      jsonPayload, agentId, response, questionId, count, gotAnswer;
  var MAX_TRIES = 10;
  var SLEEP_MS = 3000;
  
  Console.log("KD_AskQuestion");
  
  agentId = params.agentId;
  objKeys = [];
  if(input && input.length) {
    for(i = 0; i < input.length; i++) {
      objKeys.push(input.get(i).id);
    }
  }
  
  Console.log("  Calling the service...");
  jsonPayload = {
    "question": params.question,
    "contextObjectIds": objKeys
  };
  resultJsonBlob = HylandKnowledgeDiscovery.Invoke(null, {
    "httpMethod": "POST",
    "endpoint": "/agent/agents/" + agentId + "/questions",
    "jsonPayloadStr": JSON.stringify(jsonPayload)
  });
  
  resultJson = JSON.parse(resultJsonBlob.getString());
  // Expecting 202 with the questionId
  if(resultJson.responseCode !== 202) {
    Console.error("Error calling the service:\n " + JSON.stringify(resultJson, null, 2));
  } else {
    gotAnswer = false;
    response = resultJson.response;
    questionId = response.questionId;
    Console.log("  Question accepted, questionId=" + questionId);
    Console.log("  Now fetching a response with a timeout...");
    count = 0;
    do {
      count += 1;
      if(count > 3) {
        Console.warn("    This is try #" + count + "/" + MAX_TRIES);
      }
      if(count > 1) {
        java.lang.Thread.sleep(SLEEP_MS);
      }
      resultBlob = HylandKnowledgeDiscovery.Invoke(null, {
        "httpMethod": "GET",
        "endpoint": "/qna/questions/" + questionId + "/answer"
      });
      resultJsonStr = resultBlob.getString();
      resultJson = JSON.parse(resultJsonStr);
      response = resultJson.response;
      // We can have 200 and a null answer, when responseCompleteness: "submitted"
      gotAnswer = resultJson.responseCode === 200 && response && response.responseCompleteness && response.responseCompleteness.toLowerCase() === "complete";
      
    } while(count < MAX_TRIES && !gotAnswer);
  }
  
  Console.log(  "  Result:\n" + JSON.stringify(resultJson, null, 2));
  
  Console.log("KD_AskQuestion......DONE");
  
  return org.nuxeo.ecm.core.api.Blobs.createJSONBlob(resultJsonStr);

}
```

## Example Using `HylandKnowledgeDiscovery.startConversation`

In this example, we start a new conversation with an agent. Unlike `askQuestionAndGetAnswer`, the conversation API returns the answer synchronously (no polling needed). The returned `conversationId` can be stored and reused for follow-up questions.

```javascript
// input type: void
// output type: blob
function run(input, params) {

  var result = HylandKnowledgeDiscovery.startConversation(
    null, {
      "question": "What are the main security policies?"
    }
  );
  var jsonStr = result.getString();
  var json = JSON.parse(jsonStr);

  if(json.responseCode === 200) {
    // Get the conversationId for follow-up questions
    var conversationId = json.response.conversation.id;
    // Get the answer
    var answer = json.response.message.answer;
    var messageId = json.response.message.id;

    Console.log("Conversation ID: " + conversationId);
    Console.log("Answer: " + answer);
  }

  return result;
}
```

## Example Using `HylandKnowledgeDiscovery.continueConversation`

In this example, we continue an existing conversation. The agent retains context from previous messages, so follow-up questions like "tell me more about the first one" work as expected.

```javascript
// input type: void
// output type: blob
function run(input, params) {

  // Assuming we already have a conversationId from a previous startConversation call
  var conversationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

  var result = HylandKnowledgeDiscovery.continueConversation(
    null, {
      "conversationId": conversationId,
      "question": "Can you provide more details about the timeline?"
    }
  );
  var jsonStr = result.getString();
  var json = JSON.parse(jsonStr);

  if(json.responseCode === 200) {
    var answer = json.response.answer;
    var messageId = json.response.id;

    Console.log("Message ID: " + messageId);
    Console.log("Answer: " + answer);
  }

  return result;
}
```

## Example Using `HylandKnowledgeDiscovery.conversationFeedback`

In this example, we submit feedback on a conversation message.

```javascript
// input type: void
// output type: blob
function run(input, params) {

  var result = HylandKnowledgeDiscovery.conversationFeedback(
    null, {
      "conversationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "messageId": "f1e2d3c4-b5a6-7890-1234-567890abcdef",
      "feedback": "Good"
    }
  );
  var jsonStr = result.getString();
  var json = JSON.parse(jsonStr);

  if(json.responseCode >= 200 && json.responseCode < 300) {
    Console.log("Feedback submitted successfully");
  }

  return result;
}
```

## Example: Full Conversation Flow

This example shows a complete conversation flow: start a conversation, ask a follow-up, and submit feedback.

```javascript
// input type: void
// output type: blob
function run(input, params) {

  var result, jsonStr, json, conversationId, messageId;

  // 1. Start a conversation
  Console.log("Starting conversation...");
  result = HylandKnowledgeDiscovery.startConversation(
    null, {
      "question": "What types of contracts do we have?"
    }
  );
  json = JSON.parse(result.getString());
  if(json.responseCode !== 200) {
    Console.error("Failed to start conversation: " + JSON.stringify(json, null, 2));
    return result;
  }

  conversationId = json.response.conversation.id;
  messageId = json.response.message.id;
  Console.log("First answer: " + json.response.message.answer);

  // 2. Ask a follow-up question
  Console.log("Asking follow-up...");
  result = HylandKnowledgeDiscovery.continueConversation(
    null, {
      "conversationId": conversationId,
      "question": "Which ones expire this year?"
    }
  );
  json = JSON.parse(result.getString());
  if(json.responseCode === 200) {
    Console.log("Follow-up answer: " + json.response.answer);
    messageId = json.response.id;
  }

  // 3. Submit feedback on the last answer
  Console.log("Submitting feedback...");
  result = HylandKnowledgeDiscovery.conversationFeedback(
    null, {
      "conversationId": conversationId,
      "messageId": messageId,
      "feedback": "Good"
    }
  );
  json = JSON.parse(result.getString());
  Console.log("Feedback response code: " + json.responseCode);

  Console.log("Done.");
  return result;
}
```