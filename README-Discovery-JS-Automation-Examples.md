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