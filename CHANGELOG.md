# Changelog



<!-- "name: v1.6.3" is a release tag -->

## [v1.6.3](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.3) (2025-09-14)

### Features

 *  **studio/webui**  adding support for multi graph instances ([48fb2856552ba04](https://github.com/bsorrentino/langgraph4j/commit/48fb2856552ba043b045f50e21bf4c6b119f56f7))
     > work on #233
   
 *  **studio/quarkus**  adding support for multi graph instances ([df0017eef130ea7](https://github.com/bsorrentino/langgraph4j/commit/df0017eef130ea78d7ce08989cd68fc4fbb32efb))
     > work on #233
   
 *  **studio/springboot**  adding support for multi graph instances ([ccb4f9f41aafcd8](https://github.com/bsorrentino/langgraph4j/commit/ccb4f9f41aafcd8a345c12be659421882fe09bb9))
     > work on [#233](https://github.com/langgraph4j/langgraph4j/issues/233)
   
 *  **studio/jetty**  rename  LangGraphStreamingServerJetty to  LangGraphStudioServer4Jetty adding support for multi graph instances ([7efa87a18286f16](https://github.com/bsorrentino/langgraph4j/commit/7efa87a18286f16d0a442ce769a03d7315629674))
     > work on #233
   
 *  **studio**  add support for multi graph instances ([7731ff5a392984f](https://github.com/bsorrentino/langgraph4j/commit/7731ff5a392984f910246411087412b701aaf370))
     > work on #233
   
 *  **RunnableConfig**  Add metadata support for studio environment check ([10e6419cd09d70c](https://github.com/bsorrentino/langgraph4j/commit/10e6419cd09d70c30a0f615f8796ff6ed706fb71))
     > - Added a new constant &#x60;STUDIO_METADATA_KEY&#x60;.
     > - add method &#x60;isRunningInStudio()&#x60; that checks if the graph is running within a studio environment based on the value of the &#x60;STUDIO_METADATA_KEY&#x60; metadata key.
     > work on #231
   

### Bug Fixes

 -  **studio**  Ensure compileConfig is valid during instance build ([908daa20e730f85](https://github.com/bsorrentino/langgraph4j/commit/908daa20e730f8536e379a0643298933e95b2495))
     > Refactored code to check if compileConfig is null and initialize it with a default MemorySaver if necessary.
     > work on #233


### Documentation

 -  bump to version 1.6.3 ([44e1fec42027db0](https://github.com/bsorrentino/langgraph4j/commit/44e1fec42027db07f1cb7d0a0fa44f876bf7f004))

 -  **studio**  updare documentation ([cc699106571f2f7](https://github.com/bsorrentino/langgraph4j/commit/cc699106571f2f7b46e11b357c16ceaca56154d8))

 -  update README.md imports and examples ([08ad5d1470051e8](https://github.com/bsorrentino/langgraph4j/commit/08ad5d1470051e8db35c7ebdb7de0be9e9eb9971))

 -  update README.md imports and examples ([0d8f7d7a70e96b8](https://github.com/bsorrentino/langgraph4j/commit/0d8f7d7a70e96b8a40002c899b68cbfe9d8661e0))

 -  update changelog ([835db8a7db56077](https://github.com/bsorrentino/langgraph4j/commit/835db8a7db56077aa3592810094a95d0e2a7b6e2))


### Refactor

 -  **studio**  refactors the LangGraphStudioServer class by removing the unused 'start' method. ([f67a032df25f16a](https://github.com/bsorrentino/langgraph4j/commit/f67a032df25f16a8968ee51737682cc4b544798f))
    > work on #233

 -  **studio/springboot**  deprecate use of AbstractLangGraphStudioConfig and LangGraphFlow ([7a114d2538174ad](https://github.com/bsorrentino/langgraph4j/commit/7a114d2538174adacdd89b51794a8d47bafdffa1))
    > work on #233

 -  **springai-agent**  Refactored LangGraphStudioConfiguration to use Map<String, LangGraphStudioServer.Instance> ([47df74f1a89f065](https://github.com/bsorrentino/langgraph4j/commit/47df74f1a89f0659054fd63f5b05ab2c3fb6742b))
    > work on #233

 -  **studio**  move  NodeOutputSerializer in a independet file ([12780a31e1ba075](https://github.com/bsorrentino/langgraph4j/commit/12780a31e1ba0750bcb0e24f91d738f43f7e2051))
    > work on [#233](https://github.com/langgraph4j/langgraph4j/issues/233)

 -  **studio**  rename  LangGraphStreamingServer to LangGraphStudioServer ([a6d1164b9a940c5](https://github.com/bsorrentino/langgraph4j/commit/a6d1164b9a940c5b144e6216421b55a91b302652))
    > work on #233

 -  **LangGraphStreamingServer**  Add metadata key to RunnableConfig ([5e195f854068ab0](https://github.com/bsorrentino/langgraph4j/commit/5e195f854068ab0fe694ce6cb94e31b1f22f0b23))
    > adding metadata key &#x27;STUDIO_METADATA_KEY&#x27; to RunnableConfig in request handling methods.
 > work on #231

 -  Remove type parameter ([93b5442e691469e](https://github.com/bsorrentino/langgraph4j/commit/93b5442e691469e27a98436f0733836bc3e4ed63))
   
 -  **HasMetadata**  Add key validation and private prefix warning ([be5a057331450e6](https://github.com/bsorrentino/langgraph4j/commit/be5a057331450e612d7defbf5faa4c4723756861))
    > - The HasMetadata interface has been updated to include key validation. Keys that start with the private prefix &#x27;__&#x27; are now prohibited, and an IllegalArgumentException is thrown if attempted.
 > - This interface is not generic anymore
 > work on #231

 -  replace deprecated methods fields() with properties() ([3513c005a0f46a6](https://github.com/bsorrentino/langgraph4j/commit/3513c005a0f46a66d85eccccd8cff66850b5d58c))
   
 -  replace deprecated methods fields() with properties() ([34a226e149b391d](https://github.com/bsorrentino/langgraph4j/commit/34a226e149b391d85bb8e5d869ab5436b1db32e4))
   

### ALM 

 -  bump to version 1.6.3 ([78cf2c8e047589a](https://github.com/bsorrentino/langgraph4j/commit/78cf2c8e047589af4ed85636504fb9dd4f1e2c4b))
   
 -  **studio/webui**  deploy new forntend implementation ([1adaf47adcb5a4d](https://github.com/bsorrentino/langgraph4j/commit/1adaf47adcb5a4d7cc15b8e42f688a5969614803))
   
 -  bump to 1.6-SNAPSHOT ([bc8e6332aef4bb9](https://github.com/bsorrentino/langgraph4j/commit/bc8e6332aef4bb97dc412c791db373f48f65da06))
   
 -  bump langchain4j version from 1.2.0 to 1.4.0 and beta from 1.2.0-beta8 to 1.4.0-beta10. ([3cc9d6526ce8c7a](https://github.com/bsorrentino/langgraph4j/commit/3cc9d6526ce8c7ae78703c0cecb4b3db6d30256a))
   

### Test 

 -  **studio/quarkus**  fix unit test passing instance to GraphInit servlet ([b24913062be0cb3](https://github.com/bsorrentino/langgraph4j/commit/b24913062be0cb37113ba4f8937846174d62f386))
    > work on #233

 -  **studio/springboot**  testing for multi graph instances support ([4953126681b4ca0](https://github.com/bsorrentino/langgraph4j/commit/4953126681b4ca0c8891e0156034713225090e5f))
    > work on #233

 -  **studio/jetty**  testing for multi graph instances support ([088b07a9b98a303](https://github.com/bsorrentino/langgraph4j/commit/088b07a9b98a30377f2b98a9c10741a4dee3d58f))
    > work on [#233](https://github.com/langgraph4j/langgraph4j/issues/233)

 -  ckeck if `isRunningInStudio()` is correct handled when running agent in studioenvironemnt ([36f483703acb8e5](https://github.com/bsorrentino/langgraph4j/commit/36f483703acb8e58ba1432c97c76a0acc9315fdf))
    > work on #231






<!-- "name: v1.6.2" is a release tag -->

## [v1.6.2](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.2) (2025-08-28)

### Features

 *  **core**  add SubGraphOutput to better keep track of the compiled sub graph output ([38dac5a42e7c022](https://github.com/bsorrentino/langgraph4j/commit/38dac5a42e7c022072400955b072493f9024c8bd))
     > work on #216
   
 *  **Types**  Add utility class for type reflection and parameterized type handling. ([b0eac5fc92fae15](https://github.com/bsorrentino/langgraph4j/commit/b0eac5fc92fae15222f87e3ab2ecbf0ed3e35ff4))
     > introduced Types interface with methods to traverse class hierarchy and find parameterized types, as well as to get the raw class erasure of various type subtypes. These utilities can be useful for generic type reflection in Java applications.
   
 *  **TypeRef**  Add method to explicitly get erased class with type erasure ([4390fab217619e2](https://github.com/bsorrentino/langgraph4j/commit/4390fab217619e2283fc3ceb2408ce85e813f986))
   

### Bug Fixes

 -  **CallModel**  handle null AI response text gracefully ([3d3a3b5d833cb3d](https://github.com/bsorrentino/langgraph4j/commit/3d3a3b5d833cb3d6efd27677f93a4f6d00fba5f4))
     > - Add null check for content.text() in mapResult method to prevent NullPointerException

 -  **MermaidGenerator**  update naming convention in mermaid diagram for manage sub graph nodes ([b97f83bf7d0a058](https://github.com/bsorrentino/langgraph4j/commit/b97f83bf7d0a058ed944b07d9ce2226eb420d1c6))
     > work on [#216](https://github.com/langgraph4j/langgraph4j/issues/216)

 -  **SubCompiledGraphNodeAction**  Add support for resuming subgraph actions after syncing state with parent graph ([98cb193e00ea27c](https://github.com/bsorrentino/langgraph4j/commit/98cb193e00ea27c31e0340da1cd1dde8344a8000))
     > work on #218

 -  **PlantUMLGenerator**  Change rectangle to package syntax for subgraphs ([b3a8758246c9bf6](https://github.com/bsorrentino/langgraph4j/commit/b3a8758246c9bf6546dea409a8a907a2fbe37525))
     > work on #216


### Documentation

 -  update readme ([e7d9e423ee1f28f](https://github.com/bsorrentino/langgraph4j/commit/e7d9e423ee1f28f222a88851342ea914d80e3c2d))

 -  bump to version 1.6.2 ([f9f278ed6544d1a](https://github.com/bsorrentino/langgraph4j/commit/f9f278ed6544d1a0d6f2be36e3f6a3d88d45d936))

 -  update changelog ([9efa5f539e3636e](https://github.com/bsorrentino/langgraph4j/commit/9efa5f539e3636e53d79534cd278cbfc190c99ce))


### Refactor

 -  **LangGraphStreamingServer**  Refactor node output serialization for subgraphs ([1490ab2ef39ad49](https://github.com/bsorrentino/langgraph4j/commit/1490ab2ef39ad49d4f0b4966a014ebc41fe45385))
    > work on #216

 -  **PlainTextStateSerializer**  Refactor getStateType method to use Types parameterizedType for type inference ([432b75beda3b271](https://github.com/bsorrentino/langgraph4j/commit/432b75beda3b27191b43bc7b73db4608435dede5))
   
 -  **TypeRef**  Refactored TypeRef methods for better type safety and clarity ([08ec14e9c74a8d3](https://github.com/bsorrentino/langgraph4j/commit/08ec14e9c74a8d3bf509ee11ca9f31220e7a7a3b))
    > Separated concerns of getting the generic type, casting objects, and obtaining raw class into separate methods. Updated documentation for each method.

 -  **PlainTextStateSerializer**  Refactor getStateType method to return Optional<Class<State>> ([62e201c6374cb4c](https://github.com/bsorrentino/langgraph4j/commit/62e201c6374cb4cde12f55bd237216b061444a5b))
   

### ALM 

 -  bump to version 1.6.2 ([0c4fc3bd858e82e](https://github.com/bsorrentino/langgraph4j/commit/0c4fc3bd858e82e2fc164d940c87bc65c497cd4c))
   
 -  bump to SNAPSHOT ([388e433085027b9](https://github.com/bsorrentino/langgraph4j/commit/388e433085027b94ef86e66b1dc8e3ed789952bc))
   

### Test 

 -  **studio**  test nested subgraph ([b2f934758f429c1](https://github.com/bsorrentino/langgraph4j/commit/b2f934758f429c126caff4aee3a196a8a458ae18))
   





<!-- "name: v1.6.1" is a release tag -->

## [v1.6.1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.1) (2025-08-21)

### Features

 *  **core**  allows interruptions in compiled subgraph ([134b56b1022036e](https://github.com/bsorrentino/langgraph4j/commit/134b56b1022036eeafe0f150c249fcd355289572))
     > resolve #193
   
 *  **HasMetadata**  Add type-safe metadata retrieval method with TypeRef ([79bea93b7780d91](https://github.com/bsorrentino/langgraph4j/commit/79bea93b7780d9198b35eabbf715849e563202f4))
     > This commit introduces a new method &#x60;metadata(String key, TypeRef&lt;T&gt; typeRef)&#x60; to safely retrieve metadata values, enhancing the type-safety of metadata operations.
   
 *  **TypeRef**  Add TypeRef class to handle generic types ([dff653ab5c42b28](https://github.com/bsorrentino/langgraph4j/commit/dff653ab5c42b2896cf3452120710aa12bad42c2))
     > This new class inspired by Jackson&#x27;s TypeReference allows passing full generics type information and avoids problems with type erasure. It provides methods for casting objects to the specified type and handling different type arguments.
   
 *  **spring-ai-agent**  enable github-models ([f64f57ea09201fa](https://github.com/bsorrentino/langgraph4j/commit/f64f57ea09201fabae3e363c0b342daf6000c1ab))
   

### Bug Fixes

 -  **Node**  Update private prefix handling in Node.java ([59834c5cd3e2835](https://github.com/bsorrentino/langgraph4j/commit/59834c5cd3e2835b0f1ef383151134e37f5b6ef8))
     > This commit updates the private prefix handling in the &#x60;Node&#x60; class. It introduces a constant &#x60;PRIVATE_PREFIX&#x60; and uses it to validate node identifiers. This change ensures consistency and clarity in identifying private nodes.


### Documentation

 -  update readme ([d442faccbdba63b](https://github.com/bsorrentino/langgraph4j/commit/d442faccbdba63b6b7d3543e6026eed0084db525))

 -  bumpo to version 1.6.1 ([d6972a51842eab0](https://github.com/bsorrentino/langgraph4j/commit/d6972a51842eab0ad33a84523daec220d2aeeb77))

 -  update subgraph site documentation ([f3dec1c75c983a1](https://github.com/bsorrentino/langgraph4j/commit/f3dec1c75c983a1b6f6126fe2943f9803b0c2500))
     > work on #193

 -  **RunnableConfig**  update javadoc ([afc654ea232e24d](https://github.com/bsorrentino/langgraph4j/commit/afc654ea232e24d80d9461ee0abb9e9d8c48c7e4))

 -  **CompiledGraph**  Update @since tag in invokeFinal method ([a0ee6548e88b010](https://github.com/bsorrentino/langgraph4j/commit/a0ee6548e88b010246d90ea7bf87c0558063d7eb))

 -  update changelog ([c206b63dde6dd72](https://github.com/bsorrentino/langgraph4j/commit/c206b63dde6dd72eefa0db888152d3dd0abc0ce9))


### Refactor

 -  **CompiledSubGraphTest**  Add logging instead of console output ([0c3a25583817621](https://github.com/bsorrentino/langgraph4j/commit/0c3a255838176216337945c1fb7227383acded00))
   
 -  **CompiledSubGraphTest**  Add logging instead of console output ([c44dc2603ba8974](https://github.com/bsorrentino/langgraph4j/commit/c44dc2603ba8974184db02258b2803e69331298a))
   
 -  **CompiledGraph**  Refactored method signatures and updated return types for 'invoke' methods ([8c62070746fa5de](https://github.com/bsorrentino/langgraph4j/commit/8c62070746fa5de1427a861016008446a74baaa3))
    > Add a new method &#x60;invokeFinal&#x60; was added to return an Optional&lt;NodeOutput&gt; containing the final NodeOutput of the graph execution.

 -  **CompiledGraph**  Make private static string final and use public enum for error handling ([4699b9555e7676f](https://github.com/bsorrentino/langgraph4j/commit/4699b9555e7676f0da8a9f99d30543e2a8b065f3))
    > Refactored &#x27;INTERRUPT_AFTER&#x27; to be a public static final string. Changed the &#x27;RunnableErrors&#x27; enum to a public enum to improve visibility and access across different parts of the codebase.


### ALM 

 -  bumpo to version 1.6.1 ([50019b5ff0e870d](https://github.com/bsorrentino/langgraph4j/commit/50019b5ff0e870d3f9b7300aa51632f35bfdc21f))
   
 -  **spring**  update dependency version to 1.0.1 ([b59ae21c98f4d62](https://github.com/bsorrentino/langgraph4j/commit/b59ae21c98f4d62445d5512b52ba154e3f992951))
    > resolve #212

 -  bump to  1.6-SNAPSHOT ([c30f3b0eeb7b47f](https://github.com/bsorrentino/langgraph4j/commit/c30f3b0eeb7b47fd7598ccd710de0e4e75642e01))
   

### Test 

 -  setup test case scenarios for validate interruptions in compiled subgraph ([d1abb9bc9623829](https://github.com/bsorrentino/langgraph4j/commit/d1abb9bc962382967c5d4ff4e50204d3b151248c))
    > work on #193






<!-- "name: v1.6.0" is a release tag -->

## [v1.6.0](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.0) (2025-08-12)

### Features

 *  **spring-ai-agent**  enhance customizability feature ([58bb756531ed5da](https://github.com/bsorrentino/langgraph4j/commit/58bb756531ed5da5f3ab3399e8f24c0847fca889))
     > allow to provide a custom ChatClient
   
 *  add spring-ai-agent maven archetype ([f1e15817bac19c4](https://github.com/bsorrentino/langgraph4j/commit/f1e15817bac19c4d63c1017394eed08277ec6d67))
     > work on #207
   
 *  **appenderchannel**  add new `ReplaceAllWith` record for batch replacement ([788c7376647dbb4](https://github.com/bsorrentino/langgraph4j/commit/788c7376647dbb47f357823f4932f828d9bddd6b))
     > discussion #204
     > resolve #206
   

### Bug Fixes

 -  **serialization**  add support  of deserialization of all managed types by jackson ([206ab64c73e255a](https://github.com/bsorrentino/langgraph4j/commit/206ab64c73e255a310d9b27d2fec7dc5efa0b6c2))
     > resolve #211

 -  replace usage of `Serializable.writeUTF()` and `Serializable.readUTF()` with raw implementation ([86a60cb8f5c4ca5](https://github.com/bsorrentino/langgraph4j/commit/86a60cb8f5c4ca5132b31092c283d8b70d46e8da))
     > work on #209

 -  replace `out.writeUTF()` with `Serializable.writeUTF()` and `in.readUTF()` with `Serializable.readUTF()` ([eb98ac97a1cfe4c](https://github.com/bsorrentino/langgraph4j/commit/eb98ac97a1cfe4c054b63dcfc65a46fce10610d2))
     > fix [#209](https://github.com/langgraph4j/langgraph4j/issues/209)

 -  replace `out.writeUTF()` with `Serializable.writeUTF()` and `in.readUTF()` with `Serializable.readUTF()` ([ad13ca509ae67be](https://github.com/bsorrentino/langgraph4j/commit/ad13ca509ae67be9401f20969d7e60c85671db59))
     > fix #209


### Documentation

 -  bump to version 1.6.0 ([e6bc15dcca50886](https://github.com/bsorrentino/langgraph4j/commit/e6bc15dcca508863fbe61e5bf7e0ab425c47643f))

 -  **core**  update parallel nodes documentation and clarify limitations ([343f993776e2ae7](https://github.com/bsorrentino/langgraph4j/commit/343f993776e2ae720445815cb9816fbe9a717c19))
     > Provided an example of configuring an Executor using RunnableConfig.

 -  : add new `ReplaceAllWith` record for batch replacement ([aa6eab17f18aaf8](https://github.com/bsorrentino/langgraph4j/commit/aa6eab17f18aaf8e71fa19040f4b782f8246aa88))
     > discussion [#204]
     > work on [#206]

 -  update changelog ([ee71c81befe0ad9](https://github.com/bsorrentino/langgraph4j/commit/ee71c81befe0ad9931d23a5f1dafe5fdc5b4cf8a))



### ALM 

 -  bump to version 1.6.0 ([024ed90e6e2b289](https://github.com/bsorrentino/langgraph4j/commit/024ed90e6e2b2896760b617778b9df0479fc9e08))
   
 -  bump to 1.6-SNAPSHOT ([491711f8e31ffab](https://github.com/bsorrentino/langgraph4j/commit/491711f8e31ffabe77ad1865b526d82052bb67b4))
   

### Test 

 -  **langchain4j**  first test using GITHUB AI MODELS ([5286fe0ed471d93](https://github.com/bsorrentino/langgraph4j/commit/5286fe0ed471d9350a8e258a815fe30d186a6e0e))
   





<!-- "name: v1.6.0-rc4" is a release tag -->

## [v1.6.0-rc4](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.0-rc4) (2025-08-04)

### Features

 *  **core/CompiledGraph**  add support for interruption before edge evaulation ([436c18b04012249](https://github.com/bsorrentino/langgraph4j/commit/436c18b04012249331a66362f014d76028ba4322))
     > resolve #197
   
 *  **core/CompileConfig**  add flag to interrupt graph execution before evaluating conditional edges ([29b5c69d8980230](https://github.com/bsorrentino/langgraph4j/commit/29b5c69d8980230d4d715e28e0ee6068d83cef89))
     > work on #197
   
 *  **core**  add "Node Id" validation to avoid reserved name conflicts ([80d15970a66acaf](https://github.com/bsorrentino/langgraph4j/commit/80d15970a66acafa810d4ba24a991647995fe77c))
   
 *  **spring-ai**  add support for JSON state serialization through Jackson library ([5358be61ef390ab](https://github.com/bsorrentino/langgraph4j/commit/5358be61ef390ab927814d8f4c8a7e6f46904e0f))
   
 *  **spring-ai**  add guard filter for null in StreamingChatGenerator ([8141ab788add71e](https://github.com/bsorrentino/langgraph4j/commit/8141ab788add71e1beb0a0cc9f5bf1394388020b))
     > - Added a filter to ensure &#x60;response.getResult() !&#x3D; null &amp;&amp; response.getResult().getOutput() !&#x3D; null&#x60; before processing the flux
     > resolve #202
   


### Documentation

 -  bump to version 1.6.0-rc4 ([bd7f2b4a5af4e8d](https://github.com/bsorrentino/langgraph4j/commit/bd7f2b4a5af4e8d5bb4d6382e57b860b6d4300bd))

 -  update changelog ([bb33aa2ffad3bcf](https://github.com/bsorrentino/langgraph4j/commit/bb33aa2ffad3bcfd7ea83f3c6527321b9a7f0f2f))


### Refactor

 -  **core**  update deprecation annotations for removal ([261e97d869d9531](https://github.com/bsorrentino/langgraph4j/commit/261e97d869d95310dd9cb4996c056c8420f06eaf))
   

### ALM 

 -  bump to version 1.6.0-rc4 ([95b702a9de2d88c](https://github.com/bsorrentino/langgraph4j/commit/95b702a9de2d88c20d9617715c842db0c77018eb))
   
 -  bump to 1.6-SNAPSHOT ([572eccda6b8232b](https://github.com/bsorrentino/langgraph4j/commit/572eccda6b8232b7f4004cb3c2a970901635e67c))
   






<!-- "name: v1.6.0-rc3" is a release tag -->

## [v1.6.0-rc3](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.0-rc3) (2025-08-01)

### Features

 *  **core**  Add `TrySupplier` functional interface to handle Supplier with exceptions. ([2b83a97c890a86c](https://github.com/bsorrentino/langgraph4j/commit/2b83a97c890a86c540e39f743eb1f55dcb8a7291))
   

### Bug Fixes

 -  **spring-ai-agent**  update type of result in  state's attribute 'tool_execution_results' ([74e2387e2af8eba](https://github.com/bsorrentino/langgraph4j/commit/74e2387e2af8eba558509952a4bd8baf10c771a3))

 -  **postgres-saver**  update checkpoint deleting previous and inserting a new one ([03a43538df1687f](https://github.com/bsorrentino/langgraph4j/commit/03a43538df1687fabf259895f196ba232bf8b589))
     > resolve #194


### Documentation

 -  bump to version 1.6.0-rc3 ([3aaac7b063355f7](https://github.com/bsorrentino/langgraph4j/commit/3aaac7b063355f7f247138447679d0d42dceb09f))

 -  update changelog ([84b5895648fa015](https://github.com/bsorrentino/langgraph4j/commit/84b5895648fa01518785e41619bdb0f10304b010))


### Refactor

 -  **spring-ai-agent**  make Spring AI agent working with gemini-2.5-pro ([a550e1d706d2409](https://github.com/bsorrentino/langgraph4j/commit/a550e1d706d24092484a1d8da2155a956c7f8294))
    > work on #196


### ALM 

 -  bump to version 1.6.0-rc3 ([bd1267d9c9974da](https://github.com/bsorrentino/langgraph4j/commit/bd1267d9c9974daa7554762fb68517a88d2ad749))
   
 -  bump langchain4j version to 1.2.0 ([4ded04ffb1e1391](https://github.com/bsorrentino/langgraph4j/commit/4ded04ffb1e13916a5e3e13a2607eba8cb1fc064))
   
 -  bump to version 1.6-SNAPSHOT ([28bc8b9a735fa8e](https://github.com/bsorrentino/langgraph4j/commit/28bc8b9a735fa8ef67e39d066a15c31cb5c37500))
   

### Test 

 -  **core**  Add async node action executed in independent thread ([f2870d58faba5f1](https://github.com/bsorrentino/langgraph4j/commit/f2870d58faba5f1cd1261f0d7d3db93c07ca53dd))
   





<!-- "name: v1.6.0-rc2" is a release tag -->

## [v1.6.0-rc2](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.0-rc2) (2025-07-22)

### Features

 *  **core**  Add tests for parallel node with synchronous and asynchronous actions ([d48caf8febe28d6](https://github.com/bsorrentino/langgraph4j/commit/d48caf8febe28d6558f1b275fd4e616f975fcc40))
     > work on #151
   
 *  **core**  add parallel node executor runnable configuration ([746e70a1e98b37e](https://github.com/bsorrentino/langgraph4j/commit/746e70a1e98b37e0432717280c7198d822c66a8d))
     > This commit introduces a method to add custom &#x60;Executor&#x60; instances for specific parallel nodes within a &#x60;RunnableConfig&#x60;.
     > work on #151
   
 *  **core/ParallelNode**  update action evaluation methods ([19b662f44ea4dd4](https://github.com/bsorrentino/langgraph4j/commit/19b662f44ea4dd45baed9a9fd448ea462a8fcefa))
     > - Rewrite logic to support async action evaluation
     > - Separated synchronous and asynchronous action evaluation into distinct methods for better clarity and performance.
     > - Added Executor support to allow parallel actions to run on different threads, improving concurrency.
     > resolve  #151
   
 *  **core**  add conditional node with action mappings ([ac656b9924eaa2e](https://github.com/bsorrentino/langgraph4j/commit/ac656b9924eaa2e05254351c362e1740b60d5fca))
     > - Add a new method &#x60;addNode(String,AsyncCommandAction, Map) &#x60; in the &#x60;StateGraph&#x60; class that allows adding nodes which behave as conditional edges based on action mappings.
     > resolve #163
   
 *  **core**  add conditional node with action mappings ([3ab82c6a3df57d0](https://github.com/bsorrentino/langgraph4j/commit/3ab82c6a3df57d07f4e4fd2f680a6d6fce9a9be2))
     > - Add a new method &#x60;addNode(String,AsyncCommandAction, Map) &#x60; in the &#x60;StateGraph&#x60; class that allows adding nodes which behave as conditional edges based on action mappings.
     > resolve #163
   


### Documentation

 -  update Low Level Conceptual Guide ([45f5c0012039c9e](https://github.com/bsorrentino/langgraph4j/commit/45f5c0012039c9ef6837e3b7d13ee0ff8f2a31ba))

 -  bump to version 1.6.0-rc2 ([73c8b93fa31bb09](https://github.com/bsorrentino/langgraph4j/commit/73c8b93fa31bb098a9ec06c46a24e7ee6b11e0fd))

 -  add GraphInput usage ([8b0ef0c077a03d1](https://github.com/bsorrentino/langgraph4j/commit/8b0ef0c077a03d174657ccc9d4655811986022ed))

 -  update javdocs ([aefd80dc1fc575e](https://github.com/bsorrentino/langgraph4j/commit/aefd80dc1fc575eda24c6566bfd0d046e16a5d2d))

 -  update changelog ([27e3749d7d7a1df](https://github.com/bsorrentino/langgraph4j/commit/27e3749d7d7a1df3677066181252a2f05b7eea56))


### Refactor

 -  **how-to**  update to support GraphInput ([3b6c2d8fa347cf8](https://github.com/bsorrentino/langgraph4j/commit/3b6c2d8fa347cf88cedc88a4790328c1ee701eb6))
   
 -  add GraphInput to standardize graph input ([97bed35ff774179](https://github.com/bsorrentino/langgraph4j/commit/97bed35ff774179f15d81644aad8befed77eaa4c))
    > Added sealed record GraphArgs and GraphResume
 > BREAKING CHANGE: no longer support null argument to resume execution

 -  replace deprecated methods invocation ([0c32bf622b1890c](https://github.com/bsorrentino/langgraph4j/commit/0c32bf622b1890c717c08e8a7c204b81b62644d1))
   
 -  remove unused method `evaluateActionWithoutNested` ([31360eca1bbd381](https://github.com/bsorrentino/langgraph4j/commit/31360eca1bbd381b5af5076fe44301ad4bc99102))
   
 -  **spring-ai-agent**  remove @Deprecated annotation from chatModel method ([2cf4d60a1e3fc71](https://github.com/bsorrentino/langgraph4j/commit/2cf4d60a1e3fc713027c34f5fb2c001be1b7411f))
   
 -  **lcore**  return `InterruptionMetadata` for interruptible actions ([a8336e7f8adf6e5](https://github.com/bsorrentino/langgraph4j/commit/a8336e7f8adf6e58f02588dca241fc1ae3da852f))
    > - Updated return values to include &#x60;InterruptionMetadata&#x60; when interrupts are detected.


### ALM 

 -  bump to version 1.6.0-rc2 ([b8788df0b204bd4](https://github.com/bsorrentino/langgraph4j/commit/b8788df0b204bd402a32a33c3ba2e35e3236be8e))
   
 -  maven-compiler-plugin` configuration updated to specify Java release version as 17 ([862331cd956ceba](https://github.com/bsorrentino/langgraph4j/commit/862331cd956cebaaeb9471de91fa91aef08f4410))
   
 -  bump version to 1.6-SNAPSHOT ([cc30a62520d4268](https://github.com/bsorrentino/langgraph4j/commit/cc30a62520d426818e3443b5fa7c5403ba2a4f3e))
   

### Test 

 -  fix subgraph test to support GraphInput ([97d9206cbf4643d](https://github.com/bsorrentino/langgraph4j/commit/97d9206cbf4643d80e18c6ca5fa18ef24cc2378f))
   
 -  add parallel execution configuration for workflow ([90a1acf7598cfd8](https://github.com/bsorrentino/langgraph4j/commit/90a1acf7598cfd86ab1063189d2adb3a44c0ed0d))
    > work on #151






<!-- "name: v1.6.0-rc1" is a release tag -->

## [v1.6.0-rc1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.0-rc1) (2025-07-12)

### Features

 *  **agent**  add approval support for agent executor both in spring-ai and langchain4j ([6cab5a5c411d748](https://github.com/bsorrentino/langgraph4j/commit/6cab5a5c411d74896c470ed0717a84ff74d27ac8))
   
 *  **spring-ai-agent**  allows conditional tool execution approvals (HITL), enhancing control over agent actions. ([20242925ffd870a](https://github.com/bsorrentino/langgraph4j/commit/20242925ffd870ae80b519e740afb5a49e297a19))
     > Introduced an ApprovalNodeAction class to handle approval logic for tool executions.
     > Updated the State class to include additional keys and methods for tracking tool execution results and next actions.
     > Modified the dispatchTools method to handle approval for specific tool actions.
   
 *  **core**  add toString utility methods for collection and map ([4b43be82dba954f](https://github.com/bsorrentino/langgraph4j/commit/4b43be82dba954f671273d7750a6465a7d26a43f))
     > Added utility methods &#x60;toString&#x60; to improve string representation of collections and maps, enhancing readability and usability in debugging and logging scenarios.
   
 *  **core**  improve state management by introducing marker objects for reset and removal ([4a4a875afc71c1d](https://github.com/bsorrentino/langgraph4j/commit/4a4a875afc71c1db43d8e9b30e9f16497009543d))
     > - Added &#x60;MARK_FOR_RESET&#x60; and &#x60;MARK_FOR_REMOVAL&#x60; constants to mark entries for special handling.
   
 *  **core**  add support for dynamic interruptable actions ([9bb0eb086815131](https://github.com/bsorrentino/langgraph4j/commit/9bb0eb086815131e3e1a9d3069a2d94023b7a101))
     > - Added &#x60;InterruptableAction&#x60; interface to allow actions to signal interruptions.
     > - Added &#x60;InterruptionMetadata&#x60; that holds interruption info.
     > resolve #173
   

### Bug Fixes

 -  **NullableObjectSerializer**  switch from `readUTF/writeUTF` to `readFully/write` for binary serialization ([04fc22ad6134732](https://github.com/bsorrentino/langgraph4j/commit/04fc22ad6134732ef652f51f3796ce312ddb457f))
     > resolve on #183


### Documentation

 -  bump version to 1.6.0-rc1 ([f93bf49eb92d086](https://github.com/bsorrentino/langgraph4j/commit/f93bf49eb92d086c5248f2e56a0c0c0e165f5ab1))

 -  **site**  add dynamic interruption tutorial ([3cc226512fae209](https://github.com/bsorrentino/langgraph4j/commit/3cc226512fae209026668cd8800fc691b904672b))

 -  update changelog ([ea6599ed2a4755f](https://github.com/bsorrentino/langgraph4j/commit/ea6599ed2a4755f1d1d998b4407599e017f21f69))


### Refactor

 -  **core**  refine HasMetadata interface ([074d21da5604991](https://github.com/bsorrentino/langgraph4j/commit/074d21da56049918c91b6f9d279d3fcd768da912))
   
 -  update snapshots repo url ([dae118fffc5499f](https://github.com/bsorrentino/langgraph4j/commit/dae118fffc5499f5cb90420a370dd766b2a3a309))
   
 -  **deploy-snapshot**  update deployment process to support  new sonatype central server ([70177fc588d4f8f](https://github.com/bsorrentino/langgraph4j/commit/70177fc588d4f8f737ce1acbd710e783f1e53564))
   

### ALM 

 -  bump version to 1.6.0-rc1 ([d660d93ecf99cfa](https://github.com/bsorrentino/langgraph4j/commit/d660d93ecf99cfabae9d027d44f890c6540be053))
   
 -  update langchain4j dependencies ([8cd16c1453f1c1b](https://github.com/bsorrentino/langgraph4j/commit/8cd16c1453f1c1bd187f773437a27ffba38283b0))
    > - Bump langchain4j version: from 1.0.1 to 1.1.0, add rc and beta version 1.1.0-rc1 and 1.1.0-beta7 respectively.

 -  **core**  bump async-generator version to 3.2.2 ([bb9717b0ae71a86](https://github.com/bsorrentino/langgraph4j/commit/bb9717b0ae71a860c265626cb40e95c80399f823))
   
 -  bump to 1.6-SNAPSHOT ([89f541e13b48f78](https://github.com/bsorrentino/langgraph4j/commit/89f541e13b48f78b5e5a4edc0f010bb8830616a6))
   
 -  uncomment site distribution management coordinates ([3459c5ff70609cb](https://github.com/bsorrentino/langgraph4j/commit/3459c5ff70609cb38172ff14d0aeaf4db38468b4))
   
 -  update username for sonatype-central repository ([0cdfd6ce92a4580](https://github.com/bsorrentino/langgraph4j/commit/0cdfd6ce92a458089206a830f2c18679795f7181))
   






<!-- "name: v1.6.0-beta6" is a release tag -->

## [v1.6.0-beta6](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.0-beta6) (2025-07-06)

### Features

 *  **langchain4j-core**  introduce ChatMessageUtil to provide type-safe message checks and conversions ([6f9920264efbf27](https://github.com/bsorrentino/langgraph4j/commit/6f9920264efbf27b816c3dca1d251020aabcb30c))
   
 *  **spring-ai-core**  introduce MessageUtil to provide type-safe message checks and conversions ([834d7e0e355b0e8](https://github.com/bsorrentino/langgraph4j/commit/834d7e0e355b0e82a4ec2ca97ee7e23c34db5dcf))
   

### Bug Fixes

 -  **JacksonStateSerializer**  switch from `writeUTF` to `writeObject` for JSON serialization ([c9d0ff268f55f68](https://github.com/bsorrentino/langgraph4j/commit/c9d0ff268f55f688f062df54e8803ffe1aa53716))
     > this is overhelm size limit of 65535 chars
     > resolve #181

 -  **core**  update return value in shouldInterruptBefore ([05bce20916940a8](https://github.com/bsorrentino/langgraph4j/commit/05bce20916940a8d83a3bad9e5e869cfa804fd23))


### Documentation

 -  bump to version 1.6.0-beta6 ([8419cd7374ca9dc](https://github.com/bsorrentino/langgraph4j/commit/8419cd7374ca9dc19648e81164f718f2016d733a))

 -  update changelog ([62076723c1aa0e9](https://github.com/bsorrentino/langgraph4j/commit/62076723c1aa0e9257716b235865f8af2da9c600))


### Refactor

 -  update deployment process to support  new sonatype central server ([4308ae76bd09a3f](https://github.com/bsorrentino/langgraph4j/commit/4308ae76bd09a3f71af9fe95ed9c379f2e3bd0ff))
   

### ALM 

 -  bump to version 1.6.0-beta6 ([20e148e6406703f](https://github.com/bsorrentino/langgraph4j/commit/20e148e6406703ff5245e36844abf76d5e742832))
   
 -  bump to 1.6-SNAPSHOT ([e0227bfcbbab490](https://github.com/bsorrentino/langgraph4j/commit/e0227bfcbbab490beaab6469d35d76f124984055))
   

### Test 

 -  **JacksonStateSerializer**  switch from `writeUTF` to `writeObject` for JSON serialization ([4c4710f546a8dee](https://github.com/bsorrentino/langgraph4j/commit/4c4710f546a8dee60a8e506ed948f1d306ac9595))
    > this is overhelm size limit of 65535 chars
 > work on #181






<!-- "name: v1.6.0-beta5" is a release tag -->

## [v1.6.0-beta5](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.0-beta5) (2025-06-27)

### Features

 *  **core**  add method to check if current output node refers to the start of graph processing ([85e2e026866d2f6](https://github.com/bsorrentino/langgraph4j/commit/85e2e026866d2f6ce9e0c6208b0859f610c332c4))
     > - Refactored JacksonSerializerTest methods to use &#x27;objectToBytes&#x27; and &#x27;bytesToObject&#x27;.
   

### Bug Fixes

 -  **Checkpoint**  Introduced a copyOf() static factory method that creates an immutable copy of a Checkpoint with a new id ([097c568083b982a](https://github.com/bsorrentino/langgraph4j/commit/097c568083b982ac40d3da8562ea98359d6acc5b))
     > - Refactored state update logic to create a new Checkpoint instance
     > resolve #175

 -  **PostgresSaver**  fix SQL syntax  and Update the SQL query to explicitly cast the `state_data` column to type `jsonb`, ([7b911449898e85a](https://github.com/bsorrentino/langgraph4j/commit/7b911449898e85a033bc5199fede123951d48966))
     > resolve #174


### Documentation

 -  bump to version 1.6.0-beta5 ([da77704f1ec3e7d](https://github.com/bsorrentino/langgraph4j/commit/da77704f1ec3e7df0fc4a9238ac1841896272a7a))

 -  update site documentation ([71cd3edb0cb093b](https://github.com/bsorrentino/langgraph4j/commit/71cd3edb0cb093b0f7ec6569262962e935f18abf))

 -  update changelog ([45bd939882a1001](https://github.com/bsorrentino/langgraph4j/commit/45bd939882a1001e4fbd2c0ac6134815e3236687))


### Refactor

 -  **howto**  update wait-fo-user-input notebook to verify postgres saver usage ([1ba4160dcb5a929](https://github.com/bsorrentino/langgraph4j/commit/1ba4160dcb5a9294e4c39bc28abecf449a2e56bc))
    > work on #175

 -  **CompiledGraph**  update checkpoint handling ([8fb5d914771338e](https://github.com/bsorrentino/langgraph4j/commit/8fb5d914771338ebcc054b52323ead854c9870a2))
    > - Replaced &#x60;Checkpoint::new&#x60; with &#x60;Checkpoint::copyOf&#x60; for creating immutable checkpoints.
 > work on #175


### ALM 

 -  bump to version 1.6.0-beta5 ([614e76883d99a1b](https://github.com/bsorrentino/langgraph4j/commit/614e76883d99a1bd34e0a2aea17acf480054df53))
   
 -  **core**  bump async-generator to 3.2.1 ([1c5d857ca9f16b7](https://github.com/bsorrentino/langgraph4j/commit/1c5d857ca9f16b79a78b1d4c64179b9af39cc4a0))
   
 -  bump to 1.6-SNAPSHOT ([991a8892f96c71c](https://github.com/bsorrentino/langgraph4j/commit/991a8892f96c71cb4a95f9f4624cd8911af5392d))
   






<!-- "name: v1.6.0-beta4" is a release tag -->

## [v1.6.0-beta4](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.0-beta4) (2025-06-19)

### Features

 *  **PostgresSaver**  add module langgraph4j-postgres-saver ([02420db2871e873](https://github.com/bsorrentino/langgraph4j/commit/02420db2871e873b237259db90f5b5501149c7eb))
     > - first reference implementation
     > work on #133
   
 *  **core**  enforce attributes validation ([361c55f55dab709](https://github.com/bsorrentino/langgraph4j/commit/361c55f55dab70905a1e97dfa21995bc1234e317))
   
 *  **core**  add TryConsumer functional interface ([942754a3976230a](https://github.com/bsorrentino/langgraph4j/commit/942754a3976230a8279cb9e54579db697638952c))
     > Added a new &#x60;TryConsumer&#x60; functional interface to handle exceptions within lambda expressions
   
 *  **core**  add TryConsumer functional interface ([39e1a3e80027546](https://github.com/bsorrentino/langgraph4j/commit/39e1a3e80027546acd0521ab87099a2aa92c1caf))
     > Added a new &#x60;TryConsumer&#x60; functional interface to handle exceptions within lambda expressions
   
 *  **postgres**  start implementation of PostgresSaver ([7915c3077dc991a](https://github.com/bsorrentino/langgraph4j/commit/7915c3077dc991a368852c26384ad6fdae8b814b))
     > work #133
   

### Bug Fixes

 -  **how-to**  fix time-travel notebook replace getState() with state() ([e3652e3704eb8dd](https://github.com/bsorrentino/langgraph4j/commit/e3652e3704eb8ddb10e857d826f622ba634225d7))

 -  update parent pom relative path ([431d87e46548494](https://github.com/bsorrentino/langgraph4j/commit/431d87e46548494d9f44a5f92296e535ea0fb08a))

 -  **studio**  update parent pom path ([20bf3e315e82c7a](https://github.com/bsorrentino/langgraph4j/commit/20bf3e315e82c7a41ce28cd6c94a52b1a250a0d7))


### Documentation

 -  build: bump to version 1.6-beta4 ([0fac72c980045e4](https://github.com/bsorrentino/langgraph4j/commit/0fac72c980045e43c3259242491473d4f64d7811))

 -  **postgres**  add readme ([15a323e208458cd](https://github.com/bsorrentino/langgraph4j/commit/15a323e208458cd7b9926378c929351e3ca05ef0))
     > work on #133

 -  refine parallel node documentation ([5d5babc1864d1d1](https://github.com/bsorrentino/langgraph4j/commit/5d5babc1864d1d104275a155317d946626a05f3c))

 -  refine parallel node documentation ([e8f12334c332ab4](https://github.com/bsorrentino/langgraph4j/commit/e8f12334c332ab4dfb165bc9a9e0f8e0395a6b9f))

 -  **pom**  update project url ([276402be6de96a3](https://github.com/bsorrentino/langgraph4j/commit/276402be6de96a32e23cc6798f75366c43201dda))

 -  fix link references in site ([c517965326eb4da](https://github.com/bsorrentino/langgraph4j/commit/c517965326eb4da58ca31823872174084bb6a151))

 -  update changelog ([fe8d3337d614f7d](https://github.com/bsorrentino/langgraph4j/commit/fe8d3337d614f7d1d3abd7749f8a0062d2cb9e2d))


### Refactor

 -  **how-to**  code refinement ([8dcd0159a1c5ca1](https://github.com/bsorrentino/langgraph4j/commit/8dcd0159a1c5ca121f9a28294a23e6eb4bb63c44))
    > evaluate tool call during streaming for springai
 > work on #169

 -  **how-to**  code refinement ([edaab7cddb51d83](https://github.com/bsorrentino/langgraph4j/commit/edaab7cddb51d831898d60b38e749851bf6a6fbc))
    > evaluate tool call during streaming for springai
 > work on #169

 -  **how-to**  code refinement ([f7bfe2c14ba6612](https://github.com/bsorrentino/langgraph4j/commit/f7bfe2c14ba66125f92c7862c69661c98607d904))
    > evaluate tool call during streaming
 > work on #169

 -  **how-to**  code refinement ([cdb911c4a474333](https://github.com/bsorrentino/langgraph4j/commit/cdb911c4a4743335395bbb6e899f81a283743cee))
    > evaluate tool call during streaming
 > work on #169

 -  **serialization**  apply serialization changes ([d51faaef437b23c](https://github.com/bsorrentino/langgraph4j/commit/d51faaef437b23cc2ec796f21f9a52ac0fc87272))
   
 -  **StateSerializer**  abstract read/write methods for state data serialization ([3d4507094827ff7](https://github.com/bsorrentino/langgraph4j/commit/3d4507094827ff72859f4a2f61be1116f69335f5))
    > - Renamed &#x27;write&#x27; and &#x27;read&#x27; methods to abstract methods &#x27;writeData&#x27; and &#x27;readData&#x27; respectively.
 > - Added &#x27;dataToBytes&#x27; and &#x27;dataFromBytes&#x27; as final methods for serializing/deserializing Map&lt;String, Object&gt; instances.

 -  **Serializer**  update method names and documentation ([caef4159282526f](https://github.com/bsorrentino/langgraph4j/commit/caef4159282526f31df73c9d259bc2d9d27e47ea))
    > - Renamed &#x60;mimeType&#x60; to &#x60;contentType&#x60; for clarification.
 > - Deprecated old methods (&#x60;writeObject&#x60;, &#x60;readObject&#x60;) in favor of new ones (&#x60;objectToBytes&#x60;, &#x60;bytesToObject&#x60;).

 -  **core**  enhance MemorySaver to become a base cache implementation for further CheckpointSaver extensions ([611bbe490d64fdf](https://github.com/bsorrentino/langgraph4j/commit/611bbe490d64fdf6081fd3dc999a9577850bc112))
   
 -  **CompiledGraph**  add public access to lastStateOf method ([8819776a0f5519a](https://github.com/bsorrentino/langgraph4j/commit/8819776a0f5519a3c59cb443b542773f227023fa))
   

### ALM 

 -  bump to version 1.6-beta4 ([878166fe608cdd8](https://github.com/bsorrentino/langgraph4j/commit/878166fe608cdd8cd9414dfe3386e13173629c2b))
   
 -  **PostgresSaver**  skip integration test on build ([5034c1f81f12485](https://github.com/bsorrentino/langgraph4j/commit/5034c1f81f12485a07317f43d9c3d16b8e7d1b96))
    > work on #133

 -  **postgres**  add new module for saving checkpoint to postgres ([4b61aa3dba9a14d](https://github.com/bsorrentino/langgraph4j/commit/4b61aa3dba9a14d67cdc011d0017bc77fc415ce5))
    > work on #133

 -  bump to 1.6-SNAPSHOT ([7530493939954ee](https://github.com/bsorrentino/langgraph4j/commit/7530493939954ee74bfcd231f4584c3b0607a25c))
   

### Test 

 -  **postgres**  verify dropTable and createTable built-in commands ([d35fea32c9fa06d](https://github.com/bsorrentino/langgraph4j/commit/d35fea32c9fa06dd0dcdb61ace1f0a52f9c04ec4))
    > work on #133






<!-- "name: v1.6.0-beta3" is a release tag -->

## [v1.6.0-beta3](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.0-beta3) (2025-06-09)



### Documentation

 -  update changelog ([32611374228849b](https://github.com/bsorrentino/langgraph4j/commit/32611374228849b18d606d8747089bd1c609b50d))

 -  bump to version 1.6.0-beta3 ([76198eaf8428a6a](https://github.com/bsorrentino/langgraph4j/commit/76198eaf8428a6acad339a9e860531dd66dee360))

 -  fix link references in site ([986d1c85d871a41](https://github.com/bsorrentino/langgraph4j/commit/986d1c85d871a416189fff5fb356a098f8c2e0c6))

 -  update javadoc ([034e0915c1adc3f](https://github.com/bsorrentino/langgraph4j/commit/034e0915c1adc3f027bebfb7af6b6639f64b0755))

 -  fix javadoc links ([69e6d9b6de4ff83](https://github.com/bsorrentino/langgraph4j/commit/69e6d9b6de4ff83b20113660c9045df98e8399d2))

 -  fix javadoc links ([41afd10131b2659](https://github.com/bsorrentino/langgraph4j/commit/41afd10131b2659d06ff8233b0da9efd240d6fd9))

 -  update javadoc ([73c9c638d7232c5](https://github.com/bsorrentino/langgraph4j/commit/73c9c638d7232c5fb6c38c159e89f6b3aaf3b15a))

 -  update javadoc ([5a68979fe931b62](https://github.com/bsorrentino/langgraph4j/commit/5a68979fe931b622256352770f499bcb068cbba0))

 -  fix typo in reference link ([88c0002134ce029](https://github.com/bsorrentino/langgraph4j/commit/88c0002134ce02948cbfc8b92b7e81545930e334))
     > merge PR #160

 -  fix link references in site ([d0f9567fccafe65](https://github.com/bsorrentino/langgraph4j/commit/d0f9567fccafe6545996b4a49d00cd07cec076a9))

 -  fix bom reference ([022aa5ac1420695](https://github.com/bsorrentino/langgraph4j/commit/022aa5ac1420695a3c1b5f8a448f8bb544cc630e))
     > resolve #155

 -  update changelog ([26cd7fc76dc4b6c](https://github.com/bsorrentino/langgraph4j/commit/26cd7fc76dc4b6c0cfaf7e0ad9d20777012dd01a))



### ALM 

 -  bump to version 1.6.0-beta3 ([3db174a8e60c93b](https://github.com/bsorrentino/langgraph4j/commit/3db174a8e60c93b6abbe738401c78695a99403a9))
   
 -  bump to version 1.6-SNAPSHOT ([3eab3b2d1ae7ff8](https://github.com/bsorrentino/langgraph4j/commit/3eab3b2d1ae7ff81b85c78f4811262710b51da3e))
   






<!-- "name: v1.6.0-beta2" is a release tag -->

## [v1.6.0-beta2](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.0-beta2) (2025-06-05)



### Documentation

 -  bump to version 1.6.0-beta2 ([82d0ddae4f567b5](https://github.com/bsorrentino/langgraph4j/commit/82d0ddae4f567b53d9b9076f78238a749d58322b))

 -  update getting started ([c0e6ec4521c7484](https://github.com/bsorrentino/langgraph4j/commit/c0e6ec4521c7484082bd8b62de269f283c5680da))

 -  update getting started ([234aa25959e319a](https://github.com/bsorrentino/langgraph4j/commit/234aa25959e319a4a4fb748ed3f1936e170e0217))

 -  update getting started ([98c064f69fc5f45](https://github.com/bsorrentino/langgraph4j/commit/98c064f69fc5f4509819765ab167bd0a68baecc7))

 -  update site and readme ([4661d121c6885db](https://github.com/bsorrentino/langgraph4j/commit/4661d121c6885dbf0ad71fac4ef34a7e262d0429))

 -  update site and readme ([965561f2f75c3ba](https://github.com/bsorrentino/langgraph4j/commit/965561f2f75c3badef22a2c2ec860ad257c7e41c))

 -  update site ([6dcd3ee3d28a1a5](https://github.com/bsorrentino/langgraph4j/commit/6dcd3ee3d28a1a58aa3b0e0ed74f41c5dd2a33fd))

 -  update site ([062594f1e8866f1](https://github.com/bsorrentino/langgraph4j/commit/062594f1e8866f14a6394bf243e2f634ea50d107))

 -  update site ([6b20cabba77b8c1](https://github.com/bsorrentino/langgraph4j/commit/6b20cabba77b8c17c28b4b0bb58b2f3692abd55d))

 -  fix builder link ([d792a33a4e0f4aa](https://github.com/bsorrentino/langgraph4j/commit/d792a33a4e0f4aaeece3aa632a130a3ff1d0122e))

 -  update site ([6a2927293c627eb](https://github.com/bsorrentino/langgraph4j/commit/6a2927293c627ebca1f52d486c4731a3a7a4af08))

 -  update site ([8782b6e17bbda04](https://github.com/bsorrentino/langgraph4j/commit/8782b6e17bbda04d81556c850c827555fae4f7e0))

 -  update changelog ([47271c68e9d36fd](https://github.com/bsorrentino/langgraph4j/commit/47271c68e9d36fda97520904c62cca2f823613e7))


### Refactor

 -  **how-to**  remove useless output ([7832cfb377cd25f](https://github.com/bsorrentino/langgraph4j/commit/7832cfb377cd25f83b2f52b79214586487a355d0))
   
 -  **spring-ai-agent**  feat(AgentExecutor): add a ReACT agent that make in evidence make in evidence the tools execution using and action dispatcher node ([a8a6946473ae855](https://github.com/bsorrentino/langgraph4j/commit/a8a6946473ae855d7d6edf565fcff5a40a8f92e0))
    > - good to see in action with Studio
 > BREAKING CHANGE: ChatService is now private


### ALM 

 -  bump to version 1.6.0-beta2 ([893f4d41efbccd7](https://github.com/bsorrentino/langgraph4j/commit/893f4d41efbccd7d52fe91b9884b9e23e3e8ada3))
   
 -  bump to version 1.6-SNAPSHOT ([e96be89c71f153d](https://github.com/bsorrentino/langgraph4j/commit/e96be89c71f153d570064e2967e671f89e524f72))
   






<!-- "name: v1.6.0-beta1" is a release tag -->

## [v1.6.0-beta1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6.0-beta1) (2025-06-02)

### Features

 *  finalize usage of CommandAction as Conditional Edges action ([8d25600e6bb5ff1](https://github.com/bsorrentino/langgraph4j/commit/8d25600e6bb5ff119c205977cf70a94da91979d6))
     > resolve #109
   
 *  **StateGraph**  add node and conditional edges accepting command action ([6c4bf8b4ef272c4](https://github.com/bsorrentino/langgraph4j/commit/6c4bf8b4ef272c42a3f4c4f514593e38215a080f))
     > work on #109
   
 *  **compiledgraph**  add support command in Edge evaluation ([8db59a12f613cb0](https://github.com/bsorrentino/langgraph4j/commit/8db59a12f613cb027d609d32cd55f7433efe1997))
     > - From now conditional edge can contribute to state management
     > work on #109
   
 *  **core**  handle the gotoNode command when evaluating the next node to go to ([41de390505796d1](https://github.com/bsorrentino/langgraph4j/commit/41de390505796d11926caea4e90791c2ac56017b))
     > move enum RunnableErrors from StateGraph to CompiledGraph
     > work on #109
   
 *  **core**  enforce optional semantic to gotoNode field to Command record ([09bb03c777ee70c](https://github.com/bsorrentino/langgraph4j/commit/09bb03c777ee70c22e0479fe53c5755d7a637f90))
     > work on #109
   
 *  **core**  enforce optional semantic to gotoNode field to Command record ([0a65e51f5b66b6b](https://github.com/bsorrentino/langgraph4j/commit/0a65e51f5b66b6b002e1e7edb7301ec4acef8286))
     > work on #109
   
 *  **core**  add metadata support to RunnableConfig ([84ef52315e2278c](https://github.com/bsorrentino/langgraph4j/commit/84ef52315e2278ca655fccffc771a7bfee089c9c))
     > - Introduced &#x60;HasMetadata&#x60;
     > - Refactored &#x60;RunnableConfig&#x60; to include metadata management using builder.
     > - Added unit tests for metadata handling in &#x60;StateGraphTest&#x60;.
     > resolve #137
   
 *  **core**  add  command action support ([ccb31d7be762526](https://github.com/bsorrentino/langgraph4j/commit/ccb31d7be7625266b3ce57841445248a4098c148))
     > - Added new &#x60;Command&#x60; class  to represent a navigational command for nodes and updating properties.
     > - Implemented &#x60;AsyncCommandAction&#x60; and &#x60;CommandAction&#x60; functional interface for agent state management.
     > work on #109
   


### Documentation

 -  bump to version 1.6-SNAPSHOT ([d746aab981c719d](https://github.com/bsorrentino/langgraph4j/commit/d746aab981c719de106297feb24b5f97bede4211))

 -  bump to version 1.6.0-beta1 ([e3341adc522e574](https://github.com/bsorrentino/langgraph4j/commit/e3341adc522e574193139f49a68eab1a29e890c4))

 -  update site menu ([d82edb39006116e](https://github.com/bsorrentino/langgraph4j/commit/d82edb39006116e7b597aa8c681dd0fdd6f2c5fa))

 -  refine decumentation ([acdc0a8a6a86199](https://github.com/bsorrentino/langgraph4j/commit/acdc0a8a6a86199672218fe5da3c94802a5c6c3f))

 -  update projects links ([c4c3cba0600b274](https://github.com/bsorrentino/langgraph4j/commit/c4c3cba0600b274d3e86d767f0b1615d59e7b6df))

 -  update projects links ([808699ec3edffa7](https://github.com/bsorrentino/langgraph4j/commit/808699ec3edffa7408b5914c9eec7ac9b725514b))

 -  update projects links ([600183b762ed028](https://github.com/bsorrentino/langgraph4j/commit/600183b762ed0288246a534ee46daaeaf473ffa3))

 -  update badges ([63fc1e8ab196bcb](https://github.com/bsorrentino/langgraph4j/commit/63fc1e8ab196bcbc9db472edf688867d262aa932))


### Refactor

 -  **spring-ai-agent**  remove deprecated chatService method ([8590cc4da73cabc](https://github.com/bsorrentino/langgraph4j/commit/8590cc4da73cabc1067d48bbc49df948610fdf19))
    > BREAKING CHANGE:

 -  **agent-executor**  remove deprecated methods for chat model selection ([1e6e4a6995ff120](https://github.com/bsorrentino/langgraph4j/commit/1e6e4a6995ff1206b5b01393650948de49581083))
    > - Removed &#x60;chatLanguageModel&#x60;
 > BREAKING CHANGE:

 -  **langchain4j**  Remove deprecated ToolNode class ([1318e32652c34c9](https://github.com/bsorrentino/langgraph4j/commit/1318e32652c34c991b136c696ac1d5c0091d2d74))
    > BREAKING CHANGE:

 -  **core**  remove deprecation marked for removal ([697135a4a4f130e](https://github.com/bsorrentino/langgraph4j/commit/697135a4a4f130ec365b94946cc93ffa3fc1e234))
    > - NodeOutput removed  &#x27;getState&#x27;.
 > - Removed deprecated method of creating AppenderChannel and Channel replacing it with Channels.appender.
 > BREAKING CHANGE:

 -  **how-to**  refine implementation ([ad703b88f0afb7a](https://github.com/bsorrentino/langgraph4j/commit/ad703b88f0afb7a68cc3ff9205a9a29c5dc867aa))
    > - use EdgeMappings in multi agent notebook
 > - add workflow in plantuml notebook

 -  **how-to**  bump to version 1.6-SNAPSHOT ([8d5b297bd7c22a7](https://github.com/bsorrentino/langgraph4j/commit/8d5b297bd7c22a7f3cb92344f2f6a6596c4e657c))
   
 -  **core**  remove support for returning command from node action ([f317936a999aea0](https://github.com/bsorrentino/langgraph4j/commit/f317936a999aea0935ea82753596207528ef7194))
    > work on #109

 -  **edgeCondition**  update action type to AsyncCommandAction ([74e4d5af40aac25](https://github.com/bsorrentino/langgraph4j/commit/74e4d5af40aac25ebfc95d9d253b490af9910a2c))
    > work on #109

 -  **StateGraph**  update edge construction with AsyncCommandAction ([529c3654ec81fc2](https://github.com/bsorrentino/langgraph4j/commit/529c3654ec81fc292853cea0b7f901679e78dc70))
    > work on #109

 -  **core**  refactor to use new  `AsyncCommandAction` ([6e73884974f5fa3](https://github.com/bsorrentino/langgraph4j/commit/6e73884974f5fa3e004a8fad05e863e0776f2dfe))
    > work on #109


### ALM 

 -  bump to version 1.6.0-beta1 ([3eb0c45cf378901](https://github.com/bsorrentino/langgraph4j/commit/3eb0c45cf3789018e178ffecffc97780406cb620))
   
 -  **deploy-sitel**  fix site path ([9713fcccdd7533b](https://github.com/bsorrentino/langgraph4j/commit/9713fcccdd7533b8e30b834c2a5461e2ba0224d1))
   
 -  **deploy-sitel**  add python dependencies ([29d6f090bfe3fdd](https://github.com/bsorrentino/langgraph4j/commit/29d6f090bfe3fddac424d88c5cb0f3b3764c5074))
   
 -  bump to 1.6-SNAPSHOT ([1b65454d97e1ca1](https://github.com/bsorrentino/langgraph4j/commit/1b65454d97e1ca138cc8d7871563dff8d0d756c6))
   






<!-- "name: v1.6-20250601-1" is a release tag -->

## [v1.6-20250601-1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.6-20250601-1) (2025-06-01)

### Features

 *  **AgentExecutor**  add a ReACT agent that make in evidence make in evidence the tools execution using and action dispatcher node ([ce5a7aefb0b1bc6](https://github.com/bsorrentino/langgraph4j/commit/ce5a7aefb0b1bc6807bdad49cdb34875f5ee355d))
     > - good to see in action with Studio
   
 *  **core**  handle newValue as null in AppenderChannel ([25a760489ebbd84](https://github.com/bsorrentino/langgraph4j/commit/25a760489ebbd848685bd4a4f7ed5e744345a76b))
     > - When newValue is null the channel is reset to default value
     > BREAKING CHANGE: this change previous behaviour where the null value was ignored
   
 *  **AgentExecutor**  add a ReACT agent that make in evidence make in evidence the tools execution using and action dispatcher node ([8e9df91996cf5b6](https://github.com/bsorrentino/langgraph4j/commit/8e9df91996cf5b677d9e0f990a4b519ca44c18b1))
     > - good to see in action with Studio
   


### Documentation

 -  update changelog ([878bb035aa01f1b](https://github.com/bsorrentino/langgraph4j/commit/878bb035aa01f1b7ef73573a07a9f44ac727db48))

 -  Added documentation created with Mkdocs framework able to generate the whole langgraph4j site ([0b78ce212aca56e](https://github.com/bsorrentino/langgraph4j/commit/0b78ce212aca56e77fb4d49492a173d85e6dbbff))


### Refactor

 -  **AgentExecutor**  clean code and review readme ([1f246146ccf27b0](https://github.com/bsorrentino/langgraph4j/commit/1f246146ccf27b09500f140fe97000ad28fe90fe))
   
 -  **core/AppenderChannel**  handle newValue  equals NULL ([369ba8d2c46df95](https://github.com/bsorrentino/langgraph4j/commit/369ba8d2c46df957fd90bf33695d7f93c7d579e2))
    > -Reset to default value when newValue is null
 > BREAKING CHANGE: this change previous behaviour where the null value was ignored

 -  update project site using mkdocs ([ca73d5be1d692bd](https://github.com/bsorrentino/langgraph4j/commit/ca73d5be1d692bd2f8d13d713558e31464d72732))
    > - add build script :
 > - start maven site
 > - copy javadoc before build
 > - copy notebbok before site build
 > merge PR #150

 -  update project site using mkdocs ([98393d69abea40f](https://github.com/bsorrentino/langgraph4j/commit/98393d69abea40f42f86d0cf1e63f54985e1dbdc))
    > - add build script :
 > - start maven site
 > - copy javadoc before build
 > - copy notebbok before site build
 > merge PR #150


### ALM 

 -  add deploy site action (alpha version) ([1afac3bb2475d38](https://github.com/bsorrentino/langgraph4j/commit/1afac3bb2475d382f755038d9d22611ff7649810))
   
 -  bump to SNAPSHOT 1.5-SNAPSHOT ([b54c78f62b9cac2](https://github.com/bsorrentino/langgraph4j/commit/b54c78f62b9cac25edbcf5ebef7d5e3c7cbfc096))
   






<!-- "name: v1.5.14" is a release tag -->

## [v1.5.14](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.14) (2025-05-28)

### Features

 *  **lc4j**  add tools from MCP client ([fdf9f66272b851b](https://github.com/bsorrentino/langgraph4j/commit/fdf9f66272b851bafaeb0ac751ead1be8720398c))
   
 *  **core**  add method to retrieve last StateSnapshot for RunnableConfig ([e425f0d41a7b3b8](https://github.com/bsorrentino/langgraph4j/commit/e425f0d41a7b3b8dd1f4289df981f50fe69c58c0))
     > - Added a new method &#x60;lastStateOf&#x60; in &#x60;CompiledGraph.java&#x60; to fetch the latest &#x60;StateSnapshot&#x60; associated with a given &#x60;RunnableConfig&#x60;.
   
 *  Add comprehensive Getting Started guide ([658877ce1cd56be](https://github.com/bsorrentino/langgraph4j/commit/658877ce1cd56be6f29d605d6d198555c37433e0))
     > This commit introduces a new comprehensive &quot;Getting Started&quot; document (&#x60;GETTING_STARTED.md&#x60;) to help new users understand and use LangGraph4j.
     > The new guide covers:
     > - Introduction to LangGraph4j
     > - Core features and benefits
     > - Detailed explanation of core concepts (StateGraph, AgentState, Nodes, Edges, Compilation, Checkpoints)
     > - Installation instructions
     > - A simple, step-by-step &quot;Your First Graph&quot; example
     > - Overview of key capabilities (Async, Streaming, Persistence, Visualization, etc.)
     > - Pointers for next steps (how-tos, samples, Javadocs)
     > The main &#x60;index.md&#x60; file has been updated to:
     > - Prominently link to the new &#x60;GETTING_STARTED.md&#x60;.
     > - Reframe the existing &quot;Quick Start&quot; section as a &quot;Quick Overview&quot; for users who need a fast recap of core snippets, directing new users to the full guide.
   

### Bug Fixes

 -  **core**  update addMetadata visibility to public ([3a36c44166b36ce](https://github.com/bsorrentino/langgraph4j/commit/3a36c44166b36ce3889debf144e0ce2e22ea0163))
     > resolve issue #137


### Documentation

 -  bump to version 1.5.14 ([fcea5ebff9be3f9](https://github.com/bsorrentino/langgraph4j/commit/fcea5ebff9be3f9b72b7fa662814e981500300bb))

 -  update documentation ([81dbbd4d935eb22](https://github.com/bsorrentino/langgraph4j/commit/81dbbd4d935eb22df07da2a5865f2e607e9a37bb))

 -  update documentation ([37be59061863aa1](https://github.com/bsorrentino/langgraph4j/commit/37be59061863aa1b08839103dd238913266f5cdd))

 -  update documentation ([2f849edafabee82](https://github.com/bsorrentino/langgraph4j/commit/2f849edafabee829ee3b76fa7583e82b1d619cd9))

 -  update changelog ([b0d496e1b33f750](https://github.com/bsorrentino/langgraph4j/commit/b0d496e1b33f7507c1289388e86f84a9a387dc27))


### Refactor

 -  **AgentExecutor**  Reorganize class and methods ([aebb4693675068c](https://github.com/bsorrentino/langgraph4j/commit/aebb4693675068c5133ecff467c488b52e23db01))
    > - move Agent execution logic to CallModel.


### ALM 

 -  bump to version 1.5.14 ([be8f317b0ddca63](https://github.com/bsorrentino/langgraph4j/commit/be8f317b0ddca639e0264540aad348c60b1fa7ad))
   
 -  bump to 1.5-SNAPSHOT ([9ef3bff75482928](https://github.com/bsorrentino/langgraph4j/commit/9ef3bff754829288d160911757f1e46bd3a5cf45))
   






<!-- "name: v1.5.13" is a release tag -->

## [v1.5.13](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.13) (2025-05-21)

### Features

 *  add support for BOM ([3ae1e6951c312b8](https://github.com/bsorrentino/langgraph4j/commit/3ae1e6951c312b8c0237ca2a2c27e6b388680fd2))
     > merge  PR #146
   
 *  **core**  add metadata support to RunnableConfig ([3f98f574e5e6104](https://github.com/bsorrentino/langgraph4j/commit/3f98f574e5e6104730fd339e79108683ba2b8cd6))
     > - Introduced &#x60;HasMetadata&#x60;
     > - Refactored &#x60;RunnableConfig&#x60; to include metadata management using builder.
     > - Added unit tests for metadata handling in &#x60;StateGraphTest&#x60;.
     > resolve #137
   

### Bug Fixes

 -  **bom**  update scm connection URLs ([7c1b4b5a822e514](https://github.com/bsorrentino/langgraph4j/commit/7c1b4b5a822e514f6b9ee0dc1a449ce6077d1b3f))
     > - changed &#x60;GIT_SCM_CONNECTION_URL&#x60; from SSH to HTTPS adding scm: prefix


### Documentation

 -  bump to release 1.5.13 ([aa5ac6cd779a169](https://github.com/bsorrentino/langgraph4j/commit/aa5ac6cd779a169edc14b19414b68dcac926b532))

 -  update link to relocated project langgraph4j-builder ([f5251fec321beb0](https://github.com/bsorrentino/langgraph4j/commit/f5251fec321beb03bdd0236642004636ac7c636f))
     > work on #139

 -  update link to relocated project langgraph4j-builder ([cf8414a154d8b51](https://github.com/bsorrentino/langgraph4j/commit/cf8414a154d8b512d8c10890cf9d1c0786e7f898))
     > work on #139

 -  **spring-ai-agent**  add site ([964fee3656fee1b](https://github.com/bsorrentino/langgraph4j/commit/964fee3656fee1b81dd02a0406346d31e4ad9418))

 -  adds message to inform about relocation of sample projects ([2916696edeeb9ec](https://github.com/bsorrentino/langgraph4j/commit/2916696edeeb9ec2ae09677eb3cb28920a8ebbc3))
     > - update links
     > work on #139

 -  adds message to inform about relocation of sample projects ([5f48629cabc7048](https://github.com/bsorrentino/langgraph4j/commit/5f48629cabc70483f01d999416f0da7405398071))
     > work on #139

 -  adds message to inform about relocation of sample projects ([edbcd1fecbb1a35](https://github.com/bsorrentino/langgraph4j/commit/edbcd1fecbb1a358a849c77417b603ca2cc43e92))
     > work on #139

 -  update changelog ([62818f344b5f4e3](https://github.com/bsorrentino/langgraph4j/commit/62818f344b5f4e31268b48b19239f9799e670c60))

 -  update changelog ([ae18f63ee946f33](https://github.com/bsorrentino/langgraph4j/commit/ae18f63ee946f331e5ef3054f0ea82e342d140aa))


### Refactor

 -  **studio/jetty**  relocate test to langgraph4j-examples project ([428193ad49e143a](https://github.com/bsorrentino/langgraph4j/commit/428193ad49e143a5423b49430502f42a65c35909))
    > - remove dependencies
 > work on #139

 -  **studio/jetty**  relocate test to langgraph4j-examples project ([8706ad4a7c669d9](https://github.com/bsorrentino/langgraph4j/commit/8706ad4a7c669d98cc76375ad00e97ad8c1a2e1c))
    > work on #139


### ALM 

 -  **how-to**  bump langchain4j version to v1.0.1 ([4e93c14754e14ad](https://github.com/bsorrentino/langgraph4j/commit/4e93c14754e14ad545dc8d079393e08fbc3457a4))
    > Bump dependencies:
 > - Increase langchain4j version from 1.0.0 to 1.0.1
 > - Update beta version from 1.0.0-beta5 to 1.0.1-beta6

 -  bump to version 1.5.13 ([2476b94b8a16d6d](https://github.com/bsorrentino/langgraph4j/commit/2476b94b8a16d6d9b9c0dd41a726dd87ed89f7b6))
   
 -  bump langchain4j version to v1.0.1 ([6c04795e0c8cbaa](https://github.com/bsorrentino/langgraph4j/commit/6c04795e0c8cbaab9b172384a7e686d9213a4600))
    > Bump dependencies:
 > - Increase langchain4j version from 1.0.0 to 1.0.1
 > - Update beta version from 1.0.0-beta5 to 1.0.1-beta6

 -  update to developer version 1.5-SNAPSHOT ([cf8e6ad98c72df2](https://github.com/bsorrentino/langgraph4j/commit/cf8e6ad98c72df2762d0852109d8ad81d127c7a1))
   
 -  **generator**  remove relocated implementation ([b84f498be42ee24](https://github.com/bsorrentino/langgraph4j/commit/b84f498be42ee241d29c721d6891eaf3d5a19f1d))
    > work on #139

 -  remove relocated generator module from build ([0250f7a8d3231e2](https://github.com/bsorrentino/langgraph4j/commit/0250f7a8d3231e203171cf159a4e8095f9c025ef))
    > work on #139

 -  **samples**  remove relocated implementation ([bc110bb83ce06d3](https://github.com/bsorrentino/langgraph4j/commit/bc110bb83ce06d367a6412e93185e9f82bcefd0e))
    > work on #139

 -  remove samples modules from build ([8bd3858913cada3](https://github.com/bsorrentino/langgraph4j/commit/8bd3858913cada3070d1b72589e536be0b8ebce7))
    > work on #139







<!-- "name: v1.5.12" is a release tag -->

## [v1.5.12](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.12) (2025-05-15)


### Bug Fixes

 -  **spring-ai-agent**  to use `defaultToolCallbacks` instead of `defaultTools` ([561039f7401ea29](https://github.com/bsorrentino/langgraph4j/commit/561039f7401ea29b6002e52416fbdc174f4f7316))


### Documentation

 -  bump to version 1.5.12 ([83d49280f1f9a75](https://github.com/bsorrentino/langgraph4j/commit/83d49280f1f9a75dd00b472472e6c6bc64d4252a))

 -  add ai generated documentation ([ac04593e9ec081b](https://github.com/bsorrentino/langgraph4j/commit/ac04593e9ec081be5c8214f716ea98e540558b2e))

 -  update site documentation ([8747d7fc3dd547c](https://github.com/bsorrentino/langgraph4j/commit/8747d7fc3dd547ce3b1959fb0b6ecde2205d873c))

 -  update readme ([acc8e7d74411f41](https://github.com/bsorrentino/langgraph4j/commit/acc8e7d74411f41b9df301fa9e0786851352d850))

 -  update readme ([a2854663b16d590](https://github.com/bsorrentino/langgraph4j/commit/a2854663b16d59075212261a0acac095cd337737))

 -  update changelog ([7cd740e0d9836eb](https://github.com/bsorrentino/langgraph4j/commit/7cd740e0d9836eb5e582a6c6bfb4007d5d131ca8))


### Refactor

 -  **llm-streaming-springai**  use OllamaApi  builder ([36f13ff69dc998a](https://github.com/bsorrentino/langgraph4j/commit/36f13ff69dc998a23d10e5cd236cc438d2d839e7))
   
 -  **deploy-snapshot**  update branch ref from main to develop ([5d359d50471cd76](https://github.com/bsorrentino/langgraph4j/commit/5d359d50471cd76d2012b869b01e980b17230347))
   
 -  **deploy-pages**  apply concurrency flag to build process ([35c4dd7d55b8f3f](https://github.com/bsorrentino/langgraph4j/commit/35c4dd7d55b8f3f2dd86694b10a45f38a1528b81))
   
 -  **deploy-snapshot**  update branch trigger from main to develop ([13771c5fd0a25ce](https://github.com/bsorrentino/langgraph4j/commit/13771c5fd0a25ceb637f2671726bd6c017528fdf))
   

### ALM 

 -  bump to version 1.5.12 ([2e51063fb67628b](https://github.com/bsorrentino/langgraph4j/commit/2e51063fb67628beb1985d3da1bedf9fed796023))
   
 -  **how-to**  build: bump langchain4j version to 1.0.0 / 1.0.0-beta5 ([88aac32f9475bf2](https://github.com/bsorrentino/langgraph4j/commit/88aac32f9475bf2e20d897fd786a181e86b3575b))
   
 -  bump langchain4j version to 1.0.0 / 1.0.0-beta5 ([3c75e0c9b723258](https://github.com/bsorrentino/langgraph4j/commit/3c75e0c9b7232585eae8035c3fb8bc1314579c7c))
   
 -  **deploy-snapshot**  add  source jar generation ([d3f942fd1c591e2](https://github.com/bsorrentino/langgraph4j/commit/d3f942fd1c591e2f3197a7b8c4eb7635dee1e333))
   
 -  **site-run**  apply concurrency flag to build process ([50990ecab017753](https://github.com/bsorrentino/langgraph4j/commit/50990ecab0177534d5fd01c3bb0b8398aeea2f23))
   
 -  bump to new SNAPSHOT ([c050dc6868c9fd3](https://github.com/bsorrentino/langgraph4j/commit/c050dc6868c9fd33f35d0b490ccd93299c3f607c))
   






<!-- "name: v1.5.11" is a release tag -->

## [v1.5.11](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.11) (2025-05-13)

### Features

 *  **spring-ai-agent**  allows empty tool chain ([8d1e9298f2fb066](https://github.com/bsorrentino/langgraph4j/commit/8d1e9298f2fb0667c394dfa80ab6ad362211e48c))
     > merge #132
   
 *  **langchain4j**  allows empty tool chain ([a7cb6623972c2de](https://github.com/bsorrentino/langgraph4j/commit/a7cb6623972c2dee27291d517c0f97d2b8d62fd6))
   

### Bug Fixes

 -  **agent-executor**  update prompt ordering to feed model ([25fa3aabec6ee7f](https://github.com/bsorrentino/langgraph4j/commit/25fa3aabec6ee7f59aa54a102f861151fc71eb56))
     > resolve #128

 -  SpringAI DefaultChatService support not tools ([331a2f43a71ff94](https://github.com/bsorrentino/langgraph4j/commit/331a2f43a71ff94d62ae93c38793ba93746eb319))
     > Signed-off-by: lambochen &lt;lambochen@yeah.net&gt;


### Documentation

 -  bump to release 1.5.11 ([7b2fe23a0ad9460](https://github.com/bsorrentino/langgraph4j/commit/7b2fe23a0ad9460b6e260dc76495a09666d47fb9))

 -  update  multi-agent handoff samples links ([ea4485fcfc4ab67](https://github.com/bsorrentino/langgraph4j/commit/ea4485fcfc4ab671037c7ebb0c579217f6781c37))

 -  add multi-agent handoff samples ([ecaa645e9bd197a](https://github.com/bsorrentino/langgraph4j/commit/ecaa645e9bd197a06abcc13539b03bd3ef764b55))

 -  update changelog ([93da456742479c2](https://github.com/bsorrentino/langgraph4j/commit/93da456742479c2b9519ba70c029d72989fa26f5))



### ALM 

 -  update git ignore ([15be2a511ffcff1](https://github.com/bsorrentino/langgraph4j/commit/15be2a511ffcff1940937d420cf2730acd4658bf))
   
 -  update git ignore ([7e04d31ce9665b1](https://github.com/bsorrentino/langgraph4j/commit/7e04d31ce9665b1ba2f9298efe5319183bcfb692))
   
 -  merge PR #121 ([4a4ad5435143fb4](https://github.com/bsorrentino/langgraph4j/commit/4a4ad5435143fb4fbc24c70e5b38a7ffe7665353))
   
 -  bump to new SNAPSHOT ([3ca6104f0346574](https://github.com/bsorrentino/langgraph4j/commit/3ca6104f034657454187bc9bec8ae9e27650913d))
   






<!-- "name: v1.5.10" is a release tag -->

## [v1.5.10](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.10) (2025-05-12)

### Features

 *  **how-to**  add 'How to integrate Spring AI LLM streaming in Langgraph4j' notebook ([9772013ce53efbc](https://github.com/bsorrentino/langgraph4j/commit/9772013ce53efbc91ed61be3a7fe53e5795b29b5))
     > work on #125
   
 *  **spring-ai-agent**  add streaming chat model support ([3300c3d3dd218b3](https://github.com/bsorrentino/langgraph4j/commit/3300c3d3dd218b35412f88b4f1d1c09e7142ac59))
     > work on #125
   
 *  **spring-ai**  adds streaming chat generator ([453978e47b96754](https://github.com/bsorrentino/langgraph4j/commit/453978e47b96754669a734b4ee51345103b21013))
     > work on #125
   

### Bug Fixes

 -  **spring-ai-agent**  use `defaultToolCallbacks` instead of `defaultTools`. ([62187aa71f76d13](https://github.com/bsorrentino/langgraph4j/commit/62187aa71f76d13928c16860e7caf97f26f02b5c))


### Documentation

 -  **how-to**  update site documentation ([2463697aa257b06](https://github.com/bsorrentino/langgraph4j/commit/2463697aa257b06e0643d7be93086be9fc6afb70))

 -  bump to 1.5.10 version ([8b944f6178a9d5a](https://github.com/bsorrentino/langgraph4j/commit/8b944f6178a9d5a9736afa386b14871df8be8add))

 -  **agent-handoff**  update readme ([575b622d63f2c16](https://github.com/bsorrentino/langgraph4j/commit/575b622d63f2c161b7586fa251f5e5667a80bc09))

 -  update changelog ([4136ed144356349](https://github.com/bsorrentino/langgraph4j/commit/4136ed14435634902316b2aa7423ed2bd71d4aff))


### Refactor

 -  **spring-ai**  reorder conditional checks to improve readability and performance in `StreamingChatGenerator` ([4edd9272b0ba966](https://github.com/bsorrentino/langgraph4j/commit/4edd9272b0ba966e95ef3415ecd8501475dcb721))
    > work on #125

 -  **how-to**  update notebook title 'How to integrate Langchain4j LLM streaming in Langgraph4j' ([6dafde10e290d37](https://github.com/bsorrentino/langgraph4j/commit/6dafde10e290d37812e427bf4c4369f3a0f5e3f8))
   
 -  **NodeOutput**  remove 'isInterrupted' method and simplify JSON output in JacksonSerializerTest ([60c52c16b148d9c](https://github.com/bsorrentino/langgraph4j/commit/60c52c16b148d9c64c500eb165ef6605669b8dd7))
   
 -  **core/NodeOutput**  Add helper methods to check for graph interruption and END state ([faf574f3a3f2634](https://github.com/bsorrentino/langgraph4j/commit/faf574f3a3f2634a6835cbe4f3fd083d2fce1b9d))
    > - Introduced &#x60;isInterrupted()&#x60; method to determine if the output refers to a graph interruption by checking against non-END states.
 > - Added &#x60;isEND()&#x60; method to verify if the current node represents the end of the graph using a direct string comparison with &quot;END&quot;.
 > - Updated Javadoc comments for clarity and to reflect changes in functionality.

 -  **howto**  code refinements ([a0aadb493c1e49a](https://github.com/bsorrentino/langgraph4j/commit/a0aadb493c1e49aeef74c53ffa7d0797df93d088))
   
 -  **springai-agent**  rename project ([ae76a1b339d3cf1](https://github.com/bsorrentino/langgraph4j/commit/ae76a1b339d3cf1387be674b7af8cf1b4468cd2c))
   
 -  **sample/handoff**  change projects layout ([92960eaa56076a6](https://github.com/bsorrentino/langgraph4j/commit/92960eaa56076a635c1e6a2feb02f43d66c48081))
   

### ALM 

 -  bump to 1.5.10 version ([dc93fc9de86c8a2](https://github.com/bsorrentino/langgraph4j/commit/dc93fc9de86c8a2834807ba66f92edef760723c9))
   
 -  **core**  bump to  async-generator 3.2.0 version ([450747a01b91efd](https://github.com/bsorrentino/langgraph4j/commit/450747a01b91efd50291809fcb2f1324979c08ec))
    > work on #125

 -  **README.md**  update integrated projects to reflect Spring AI integration ([d996b67b62c30fa](https://github.com/bsorrentino/langgraph4j/commit/d996b67b62c30fa9e63016b0f53de3d13da31ae2))
   
 -  merge PR #121 ([4bb7e1082a17adf](https://github.com/bsorrentino/langgraph4j/commit/4bb7e1082a17adf77cc75740c24d439020e73df2))
   
 -  bump to new SNAPSHOT ([7518a3c9976e74d](https://github.com/bsorrentino/langgraph4j/commit/7518a3c9976e74d20b9ef2e1bad4b89ac82314a1))
   

### Test 

 -  **spring-ai-agent**  refine agent executor test implementation ([53512a825890b0d](https://github.com/bsorrentino/langgraph4j/commit/53512a825890b0d6a1d403aca91226655fcd1e4f))
   
 -  **spring-ai**  test streaming chat generator ([3827f8753825e76](https://github.com/bsorrentino/langgraph4j/commit/3827f8753825e761c9a60a80f4516d17eb92d02d))
    > work on #125

 -  **core**  alphabetically sort JSON properties for consistency ([bebca7f7dee4a05](https://github.com/bsorrentino/langgraph4j/commit/bebca7f7dee4a0589474035fde4127722e52062f))
   
 -  **core**  add optional fields to JSON output ([5b1ed3167223a70](https://github.com/bsorrentino/langgraph4j/commit/5b1ed3167223a70db6606047b781e0ad5215a187))
   





<!-- "name: v1.5.9" is a release tag -->

## [v1.5.9](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.9) (2025-05-06)

### Features

 *  **sample/handoff**  second candidate version for spring-ai implementation ([54c371a43543c5b](https://github.com/bsorrentino/langgraph4j/commit/54c371a43543c5b79bf1ffed15d745ac44ce218d))
     > work on #94
   
 *  **sample/handoff**  first candidate version for spring-ai implementation ([db42be92eaa0098](https://github.com/bsorrentino/langgraph4j/commit/db42be92eaa0098798b6554e16b3d67b70f83758))
     > work on #94
   
 *  **sample/handoff**  setup for springai support ([29d50a12d34fd2c](https://github.com/bsorrentino/langgraph4j/commit/29d50a12d34fd2c8014d77539f7489a2bb96c526))
   
 *  **sample/handoff**  refine handoff sample ([64f07d3ac76a52f](https://github.com/bsorrentino/langgraph4j/commit/64f07d3ac76a52fff851f1f5dcd19276bd23015e))
     > work on #94
   
 *  **sample/handoff**  finalize handoff sample using using agent-executor and langchain4j ([fed3d87702df5e2](https://github.com/bsorrentino/langgraph4j/commit/fed3d87702df5e2fe62ee22b2a20c288eb0324d7))
     > resolve #94
   
 *  **langchain4j**  introduce new class LC4jToolMapBuilder to build a map of ToolSpecifications and ToolExecutors ([5e74f918db03ad2](https://github.com/bsorrentino/langgraph4j/commit/5e74f918db03ad20d7e806291392187e1fcf08f4))
     > - Refactor LC4jToolService
     > - Refactor ToolNode
     > - Deprecate the older &#x60;Specification&#x60; record in favor of the more versatile and flexible map-based approach.
   
 *  **sample/handoff**  implementation based on handoff agents as functions ([ea1549410e319b9](https://github.com/bsorrentino/langgraph4j/commit/ea1549410e319b922f002bfc967616b26c06f9a5))
     > work on #94
   
 *  **sample/handoff**  start experimentation with  multi agent handoff  workflow ([a65f5c6a1e8019e](https://github.com/bsorrentino/langgraph4j/commit/a65f5c6a1e8019e916cfb48b0a78174dddd31847))
     > work on #94
   


### Documentation

 -  **springai-agent**  update readme ([723ae83ef451082](https://github.com/bsorrentino/langgraph4j/commit/723ae83ef451082333c2be80e5c73f83827e31fa))

 -  bump to release 1.5.9 ([fc28dea3e90d784](https://github.com/bsorrentino/langgraph4j/commit/fc28dea3e90d784344b74145f9dd72c65fff8d32))

 -  **sample/handoff**  update readme ([02fc3f3455408f1](https://github.com/bsorrentino/langgraph4j/commit/02fc3f3455408f1096f89aaf0f8fb11a2ef177f5))
     > work on #94

 -  **sample/handoff**  update readme ([a986f829190f209](https://github.com/bsorrentino/langgraph4j/commit/a986f829190f2096b51f1f5d223e1b409f393c78))

 -  **sample/handoff**  update readme ([e2c68a25c49029a](https://github.com/bsorrentino/langgraph4j/commit/e2c68a25c49029a9059d851c4ae8b0bf41bfc954))

 -  **sample/handoff**  update readme ([89a37d070fca964](https://github.com/bsorrentino/langgraph4j/commit/89a37d070fca9646047ca8757ee7507a93d40a4d))

 -  **sample/handoff**  update readme ([720f1cf0fc09c3a](https://github.com/bsorrentino/langgraph4j/commit/720f1cf0fc09c3a6215bfeaafe494dee5e7764b4))

 -  **samples/handoff**  update readme ([be111d5225c23dc](https://github.com/bsorrentino/langgraph4j/commit/be111d5225c23dc4c4db07f1f53e0b02b4a32590))

 -  **samples/handoff**  update readme ([89d062a44657edb](https://github.com/bsorrentino/langgraph4j/commit/89d062a44657edb23389d9ba786ed170d7380bbb))

 -  **samples/handoff**  update readme ([1501f381478b4ff](https://github.com/bsorrentino/langgraph4j/commit/1501f381478b4ffb6ddf7a5256d4dc11999725de))

 -  update home page ([55095c9f1cd36b8](https://github.com/bsorrentino/langgraph4j/commit/55095c9f1cd36b8fa8fbd9daa0e486b0c7750548))

 -  update README ([036fb4e9b2963a4](https://github.com/bsorrentino/langgraph4j/commit/036fb4e9b2963a45d308603d5afd947573397107))

 -  update README ([0f6292dedbc9303](https://github.com/bsorrentino/langgraph4j/commit/0f6292dedbc9303e02c7bf69b69611b68083e211))

 -  fix link for spring-ai-agent ([60bb6f22b5acda2](https://github.com/bsorrentino/langgraph4j/commit/60bb6f22b5acda2f9bad027c857effece952864a))

 -  fix link for spring-ai-agent ([f3e622eda636d8c](https://github.com/bsorrentino/langgraph4j/commit/f3e622eda636d8c0bcee479efc5f216ad5f6fca0))

 -  update changelog ([64637f0ac4b11d0](https://github.com/bsorrentino/langgraph4j/commit/64637f0ac4b11d00de427318b03ee7a38bfec79a))


### Refactor

 -  **springai-agent**  support chat models and tools improvements ([9caa4c170614af5](https://github.com/bsorrentino/langgraph4j/commit/9caa4c170614af525b8351c97706c73e9f5a8835))
    > - Deprecate ChatService interface
 > - Refactored DemoConsoleController to use ChatModel and ToolCallback list
 > - Refactored LangGraphStudioConfiguration to accept ChatModel and ToolCallback list

 -  bump to langchain4j 1.0.0-rc1 ([762e3e4656755a8](https://github.com/bsorrentino/langgraph4j/commit/762e3e4656755a84ff2f228a8e4ae4d2f3b6caa8))
    > - rename ChatLanguageModel to ChatModel

 -  **studio/jetty**  refactored agent instantation   by replacing direct `specification` calls with more specific methods like `toolsFromObject` ([5916e5bb3023a82](https://github.com/bsorrentino/langgraph4j/commit/5916e5bb3023a82661da4e261dce3382d08b6a13))
   
 -  **lc4jtoolservice**  update tool configuration ([d4b3c3056a0d9c1](https://github.com/bsorrentino/langgraph4j/commit/d4b3c3056a0d9c1e969955c74f0e1de4a66cc990))
    > Refactored tool service by replacing direct &#x60;specification&#x60; calls with more specific methods like &#x60;toolsFromObject&#x60;, &#x60;tool&#x60;

 -  **how-to**  bump to langgraph4j 1.0.0-beta3 ([71077fd75d5570e](https://github.com/bsorrentino/langgraph4j/commit/71077fd75d5570e5fc5fdc14a6462d37182fa7d6))
    > - remove usage of deprecated lm.checkAccess() method

 -  **agent-executor**  simplify objects hierarchy ([96126dbe3e72fa8](https://github.com/bsorrentino/langgraph4j/commit/96126dbe3e72fa864463d83f8bd3b8f6e9afb341))
   
 -  **agent-executor**  simplify objects hierarchy ([2f1d462664b7948](https://github.com/bsorrentino/langgraph4j/commit/2f1d462664b7948da54c87bd755120a7287ce18b))
   
 -  **core/CollectionsUtils**  allow null values in collection factory methods ([a678136a1cdee03](https://github.com/bsorrentino/langgraph4j/commit/a678136a1cdee0369be24f9a642553b69bb9c549))
    > - Updated methods to handle null values gracefully.
 > - Removed deprecated annotations.


### ALM 

 -  **spring-ai**  add pring-ai-client-chat deps ([f254baf15261032](https://github.com/bsorrentino/langgraph4j/commit/f254baf15261032dbaf35b39ad3a4e668ca7c93b))
   
 -  add maven wrapper support ([08ddc80315c510e](https://github.com/bsorrentino/langgraph4j/commit/08ddc80315c510e99e62762bd839c4926644f5b3))
   
 -  **pom.xml**  bump langchain4j version to 1.0.0-beta3 ([2b3e632212e20bc](https://github.com/bsorrentino/langgraph4j/commit/2b3e632212e20bc9656d4c1266b841266de9e793))
   
 -  bump to new SNAPSHOT ([624990b697d8dc0](https://github.com/bsorrentino/langgraph4j/commit/624990b697d8dc0c76a7cfe347f4bf7c8fb33785))
   

### Test 

 -  verify issue #118 ([6ac2adcd47b7313](https://github.com/bsorrentino/langgraph4j/commit/6ac2adcd47b73131ba781db7afe5da68a7f7ed98))
   
 -  **samples/agents-handoff**  try to create the complete context  for passing over handoff ([f5528a0a8316cb2](https://github.com/bsorrentino/langgraph4j/commit/f5528a0a8316cb2acbb6030d84b60f1f2a178377))
    > work on #94






<!-- "name: v1.5.8" is a release tag -->

## [v1.5.8](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.8) (2025-04-26)

### Features

 *  **studio/webui**  enhance UI adding the execution history ([757ecc46cd69546](https://github.com/bsorrentino/langgraph4j/commit/757ecc46cd69546dc1fd22a5804ce263f30e918c))
   
 *  **studio/webui**  add debug utility ([04628e2ce4851ef](https://github.com/bsorrentino/langgraph4j/commit/04628e2ce4851efbc965c3c5fa4a8cd40b14e6ad))
   
 *  **studio/webui**  add LIFO stack implementation ([e4e1c4e243eb098](https://github.com/bsorrentino/langgraph4j/commit/e4e1c4e243eb098c447372eb8feb10c50e1aa6de))
   
 *  **DiagramGenerator**  add conditional to ensure title is optional ([1e3b90dac728906](https://github.com/bsorrentino/langgraph4j/commit/1e3b90dac728906b6532ec219c11e6960c17c9e1))
     > - Added default title &#x27;unnamed&#x27; if none provided.
   
 *  add studio support to langchain4j agent executor ([3cbfc36fb3dacfb](https://github.com/bsorrentino/langgraph4j/commit/3cbfc36fb3dacfbaa1eda53b318508d37ea875ec))
   
 *  promote spring.ai agent executor as first class module ([b6ad73bb8b9c5ba](https://github.com/bsorrentino/langgraph4j/commit/b6ad73bb8b9c5ba134f401341fb6a2662f4e57e6))
   
 *  **core**  Add a versioned memory saver ([aa6dc841b65145f](https://github.com/bsorrentino/langgraph4j/commit/aa6dc841b65145f511843cbaa40f54570568f5cf))
     > - add &#x60;VersionedMemorySaver&#x60;, which extends the functionality of an existing memory saver to include versioning.
     > work on #111
   
 *  **core**  add HasVersions interface ([7d959110c60589f](https://github.com/bsorrentino/langgraph4j/commit/7d959110c60589fd8cde7b4aee4288ed8c7999b1))
     > - new interface &#x60;HasVersions&#x60; that provides methods to retrieve versions associated with a thread ID and the last version for a given thread.
     > work on #111
   


### Documentation

 -  update doc link ([a84fc805bab0fb2](https://github.com/bsorrentino/langgraph4j/commit/a84fc805bab0fb20b2784a558630c2a589f157c2))

 -  add javadoc ([9a9642dcb7069cb](https://github.com/bsorrentino/langgraph4j/commit/9a9642dcb7069cb6af3e0fb2e9a2095269bdd2e6))

 -  update changelog ([7179aa413f680d9](https://github.com/bsorrentino/langgraph4j/commit/7179aa413f680d91da1b478b0a89e8ef5dc41755))

 -  update changeme ([8f33b9c2fd9b59d](https://github.com/bsorrentino/langgraph4j/commit/8f33b9c2fd9b59d39ec1741536c90172c54b1b43))

 -  update changeme ([198f9f7c9a09d95](https://github.com/bsorrentino/langgraph4j/commit/198f9f7c9a09d95aecf2938cf1e81c9e7b378444))


### Refactor

 -  **studio/webui**  integrate debug utility ([130bf19942ed2e3](https://github.com/bsorrentino/langgraph4j/commit/130bf19942ed2e3c18ab67b96613dfb6a70387de))
   
 -  **studio/webui**  integrate debug utility ([9364fd22fefda85](https://github.com/bsorrentino/langgraph4j/commit/9364fd22fefda85af32638200fd0c122f3e4b16b))
   
 -  **studio/webui**  integrate debug utility ([bd2c91a8261f9cf](https://github.com/bsorrentino/langgraph4j/commit/bd2c91a8261f9cf5f82cbe828c5f1aabc2595e0a))
   
 -  **studio**  remove redundant graph title ([b30f10b136081df](https://github.com/bsorrentino/langgraph4j/commit/b30f10b136081dfacebf5736c24366626d06e547))
   
 -  **spring-ai-agen**  update parent group ID and version ([f5d3b70634dc5a7](https://github.com/bsorrentino/langgraph4j/commit/f5d3b70634dc5a71aaf5a436de2d55fe93ebfab1))
   
 -  moved dependencies to their correct sections. ([070c9a0982f2330](https://github.com/bsorrentino/langgraph4j/commit/070c9a0982f2330c4c542b543d427b51c63cbcb9))
   
 -  **hotfix-changelog.sh**  update script for changelog hotfix ([da8938a2ab91ce5](https://github.com/bsorrentino/langgraph4j/commit/da8938a2ab91ce545a57b1b19fe1bfd82ce17b76))
    > - Renamed branch name from &quot;changenme&quot; to &quot;changelog&quot;

 -  **Issue105Test**  remove threadVersion parameter from RunnableConfig builder ([7ce61dfd69c84ba](https://github.com/bsorrentino/langgraph4j/commit/7ce61dfd69c84ba754f8ab18e5461fa4aa63d2a1))
   

### ALM 

 -  **studio/webui**  update Jetty webui dist ([c639f905c4e858a](https://github.com/bsorrentino/langgraph4j/commit/c639f905c4e858aa51287a77ca15acb230071ec3))
    > - Update tests to verify webui enhancement

 -  **studio/webui**  update webui dist ([3389d3b18a546e0](https://github.com/bsorrentino/langgraph4j/commit/3389d3b18a546e02f95cfd2610728f4794ed4e1f))
   
 -  **studio/webui**  update webui dist ([77491f08c555561](https://github.com/bsorrentino/langgraph4j/commit/77491f08c5555617436b5bd6031452bc4a342031))
   
 -  bump to new SNAPSHOT ([9ff905f296f03c8](https://github.com/bsorrentino/langgraph4j/commit/9ff905f296f03c8c02f9cd06c773d3ae5249c5a5))
   
 -  update git ignore ([fe234a0b79cb8e7](https://github.com/bsorrentino/langgraph4j/commit/fe234a0b79cb8e7fefb70a7d34e83363e733a1f3))
   

### Test 

 -  add  a js playground ([dd58f3d0702e50e](https://github.com/bsorrentino/langgraph4j/commit/dd58f3d0702e50edf591b254d563251831afd389))
   
 -  **core**  add versioned memory saver  tests ([29f244224492b36](https://github.com/bsorrentino/langgraph4j/commit/29f244224492b36805d1ad845da780ca8afb3dbd))
    > work on #111

 -  **how-to**  refine integration test ([9ea510de9c1a6ca](https://github.com/bsorrentino/langgraph4j/commit/9ea510de9c1a6ca3668a19016f52d1e8b8001f7a))
    > - rename AgentWebTest to AgentWebITest

 -  **core**  refine multi thread scenario concerns Issue105 ([339fefa8efb2719](https://github.com/bsorrentino/langgraph4j/commit/339fefa8efb27194dc0b610e96e136bb6f110684))
   





<!-- "name: v1.5.7" is a release tag -->

## [v1.5.7](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.7) (2025-04-22)

### Features

 *  **core/CompiledGraph**  implement auto release thread ([c106f4454246b3c](https://github.com/bsorrentino/langgraph4j/commit/c106f4454246b3cb34db5287a88cd21eb7fe0ba9))
     > work on #115
   
 *  **core/CompileConfig**  add thread release flag ([73f8d7174475740](https://github.com/bsorrentino/langgraph4j/commit/73f8d71744757403212a485ca2564c57341bb42e))
     > - adding a new boolean field &#x60;releaseThread&#x60; to track whether the thread should be automatically released.
     > work on #115
   
 *  **core/FileSystemSaver**  implements release method ([5241f254b549579](https://github.com/bsorrentino/langgraph4j/commit/5241f254b549579dd0bd2fa57477bbef83b53046))
     > - introduce versioned backup of checkpoints
     > work on #115
   
 *  **core/MemorySaver**  implements release method ([b02fc6dc75f9882](https://github.com/bsorrentino/langgraph4j/commit/b02fc6dc75f98821f9f2085d2bab52885b418cb1))
     > replace ReentrantReadWriteLock with ReentrantLock for simplicity
     > work on #115
   
 *  **BaseCheckpointSaver.java**  add release method ([088785ad18b9050](https://github.com/bsorrentino/langgraph4j/commit/088785ad18b9050c1f960a2600d7fc75697831b7))
     > - Introduce &#x60;THREAD_ID_DEFAULT&#x60; to provide a default thread ID.
     > - Add a record type &#x60;Tag&#x60; to group checkpoints by thread ID.
     > work on #115
   
 *  **how-to**  add web search sample ([66c77b5f4a403d1](https://github.com/bsorrentino/langgraph4j/commit/66c77b5f4a403d195ad54d0eba58ecc72c2eaca9))
   
 *  **how-to**  add Agent Executor with MCP sample ([de5b967823d836f](https://github.com/bsorrentino/langgraph4j/commit/de5b967823d836ff1f2f1661c339f4375e977d15))
     > resolve #108
   


### Documentation

 -  bump to version 1.5.7 ([1a27fa3694bb9f1](https://github.com/bsorrentino/langgraph4j/commit/1a27fa3694bb9f1992a618efa46a2fa673b8fc7e))

 -  add new article reference ([3331f433dbeeadf](https://github.com/bsorrentino/langgraph4j/commit/3331f433dbeeadfe8d3e5b94da19071c5cee548a))

 -  **how-tos/agentexecutor-mcp**  update docker command to use 'docker compose' ([2d85a1940d36444](https://github.com/bsorrentino/langgraph4j/commit/2d85a1940d364445d25d7a9c849ab02a4a64be15))

 -  add new reference ([5be8b7f96d10b08](https://github.com/bsorrentino/langgraph4j/commit/5be8b7f96d10b08a5c68a884618c529969f5be6d))

 -  update changeme ([65b28e610bc7096](https://github.com/bsorrentino/langgraph4j/commit/65b28e610bc70961034524dd5bf9dd3af5701b35))

 -  update changeme ([97e085eff75a021](https://github.com/bsorrentino/langgraph4j/commit/97e085eff75a0212dfe313eadd5f16fc28bdaa23))

 -  update changeme ([110ca8805f650cd](https://github.com/bsorrentino/langgraph4j/commit/110ca8805f650cd01b98880f52016628216e4d8d))

 -  update changeme ([9ac2ec6fb5cf551](https://github.com/bsorrentino/langgraph4j/commit/9ac2ec6fb5cf55114ca9c7b9c2baccff58f5b8f7))

 -  update changeme ([6ed0a0b0bee4920](https://github.com/bsorrentino/langgraph4j/commit/6ed0a0b0bee4920b18ce35676d55440bf4f17288))


### Refactor

 -  **core/checkpoint**  handle null checkpoints parameter ([de7e45c63c3c0d8](https://github.com/bsorrentino/langgraph4j/commit/de7e45c63c3c0d80aa35f2ee3efa9cfdeaac7161))
    > work on #115

 -  **core/FileSystemSaver**  Removed redundant `targetFolder` variable ([4692dc0a927e977](https://github.com/bsorrentino/langgraph4j/commit/4692dc0a927e97769fb3b2461890f0e2d677d475))
    > work on #115

 -  **langchain4j**  reduce log level for null ID warning ([58953f39aad4659](https://github.com/bsorrentino/langgraph4j/commit/58953f39aad465934e06c39fc2036a74efd4cc98))
    > - ToolExecutionResultMessageSerializer
 > - ToolExecutionRequest

 -  **agent-executor**  handle STOP finish reason ([2b9bb1f3cd1812d](https://github.com/bsorrentino/langgraph4j/commit/2b9bb1f3cd1812dc32b4d31ca2a0a47e1baa6093))
    > - Add default response for empty tool execution requests

 -  **core/parallelnode**  change HashMap to ConcurrentHashMap for thread safety ([f678778c8fb836f](https://github.com/bsorrentino/langgraph4j/commit/f678778c8fb836fe84f8b1e86c7b0a901c61d159))
    > resolve #106


### ALM 

 -  bump to version 1.5.7 ([92f347f3b0553a1](https://github.com/bsorrentino/langgraph4j/commit/92f347f3b0553a158017d4d708eece1c6194ad23))
   
 -  update git ignore ([316c219399291ef](https://github.com/bsorrentino/langgraph4j/commit/316c219399291ef7a4418d1f3f43b5114b01103e))
   
 -  bump to SNAPSHOT ([5d0f228d669f6bf](https://github.com/bsorrentino/langgraph4j/commit/5d0f228d669f6bfb4c8fbfe7674e36fb40a64301))
   

### Test 

 -  **core**  Added tests for checkpoint saving with manual and auto release ([78b6840ff04135f](https://github.com/bsorrentino/langgraph4j/commit/78b6840ff04135f4b2837af8d22886436e14640b))
    > work on #115

 -  **core**  refine multi thread scenario concerns Issue105 ([5fa489a88715ce1](https://github.com/bsorrentino/langgraph4j/commit/5fa489a88715ce1d87a07d75637b4d594119706b))
   
 -  **how-to**  refine integration test ([0a563a81e54bb9e](https://github.com/bsorrentino/langgraph4j/commit/0a563a81e54bb9e329586ca76a340f288754cd07))
    > - rename AgentWebTest to AgentWebITest






<!-- "name: v1.5.6" is a release tag -->

## [v1.5.6](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.6) (2025-04-16)

### Features

 *  **creator**  add save and load graph from builder UI ([75d8725d25b2da9](https://github.com/bsorrentino/langgraph4j/commit/75d8725d25b2da9f0f0bd6006910ee4f7878c4bb))
   


### Documentation

 -  update changeme ([6e74eccf24c7ebc](https://github.com/bsorrentino/langgraph4j/commit/6e74eccf24c7ebcbdc184f1e539e3e6d3dabf369))

 -  update changeme ([40fc569e4808a51](https://github.com/bsorrentino/langgraph4j/commit/40fc569e4808a51c26e8a826b893d25c5c7553fd))

 -  update changeme ([8018c3410f5827d](https://github.com/bsorrentino/langgraph4j/commit/8018c3410f5827d64588d8488c9d9502f3470e21))

 -  update changeme ([853fbaceaa1b4a6](https://github.com/bsorrentino/langgraph4j/commit/853fbaceaa1b4a6ff2cc0c2ee6d48c7ecb7da1bb))

 -  update changeme ([d423c2b253ffbf6](https://github.com/bsorrentino/langgraph4j/commit/d423c2b253ffbf68a22928e74a895037156006ce))

 -  update changeme ([c77d8715f4ca05a](https://github.com/bsorrentino/langgraph4j/commit/c77d8715f4ca05a8acaac6bcb93275b59067e4c1))

 -  bump to release 1.5.6 ([936d8beebdae320](https://github.com/bsorrentino/langgraph4j/commit/936d8beebdae32055a98258f36eaef0216040e64))

 -  update changeme ([8f29e83248b087c](https://github.com/bsorrentino/langgraph4j/commit/8f29e83248b087c364175bb8cec92a7bcc7ab6bf))


### Refactor

 -  **core/ParallelNode.java**  change HashMap to ConcurrentHashMap for thread safety ([1a1ecf2a8a24048](https://github.com/bsorrentino/langgraph4j/commit/1a1ecf2a8a24048aad051aa903922587e838de2e))
    > resolve #106


### ALM 

 -  bump to release 1.5.6 ([ff3bfd529ff6c32](https://github.com/bsorrentino/langgraph4j/commit/ff3bfd529ff6c32b83b8a0075b6ad4e9fb2c242e))
   
 -  **core**  update async-generator dependency version to 3.1.0 ([fe0d9157d624888](https://github.com/bsorrentino/langgraph4j/commit/fe0d9157d624888baf777aeb9df01d5a9818b68c))
   
 -  **creator**  bump version of doker image to SNAPSHOT ([80e50ca56bc7fa6](https://github.com/bsorrentino/langgraph4j/commit/80e50ca56bc7fa6abdb17302450315bd801fcc13))
   
 -  bump to new SNAPSHOT ([8f314dedb108fa1](https://github.com/bsorrentino/langgraph4j/commit/8f314dedb108fa1ec30c4b099e8609c292eee875))
   

### Test 

 -  **core**  add test method for AsyncGenerator result retrieval ([830a1e12301ae15](https://github.com/bsorrentino/langgraph4j/commit/830a1e12301ae15b0e2643f5103ba0d682424a8c))
   





<!-- "name: v1.5.5" is a release tag -->

## [v1.5.5](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.5) (2025-04-09)

### Features

 *  **studio**  Add support for argument conversion. ([75ba3e8615d0b69](https://github.com/bsorrentino/langgraph4j/commit/75ba3e8615d0b6941ff99f00bc57906c21fc5770))
     > - Extend ArgumentMetadata to include a converter function
     > - Modify request processing logic to apply converters during data deserialization
   
 *  **core**  Added util method for creating immutable entries ([26e36d73f15abbd](https://github.com/bsorrentino/langgraph4j/commit/26e36d73f15abbd2590bcc3aef771bb6ad16937d))
     > - Added entryOf method to CollectionsUtils class
   

### Bug Fixes

 -  **langchain4j**  add ToolExecutionResultMessage support ([c16a75d942b76c3](https://github.com/bsorrentino/langgraph4j/commit/c16a75d942b76c35c64a688cdbc6f0e98fb87c96))
     > - Handle toolExecutionRequests in AiMessageDeserializer
     > - Added ToolExecutionResultMessage serialization/deserialization in LC4jJacksonStateSerializer.
     > - Added serialization tests for AiMessage


### Documentation

 -  bump to version 1.5.5 ([ed5dd12e55e7053](https://github.com/bsorrentino/langgraph4j/commit/ed5dd12e55e705379b19e955f598ec66675554d8))

 -  update changeme ([b9eaa38be2e977d](https://github.com/bsorrentino/langgraph4j/commit/b9eaa38be2e977d3651d2c4ce40097825845e9c0))


### Refactor

 -  **CallAgent class**  fix finish reason check ([f641de02f123b15](https://github.com/bsorrentino/langgraph4j/commit/f641de02f123b1596ddb2aae87b27bdac12811c8))
    > add langchain4j-ollama dependency for Ollama support

 -  **AppenderChannel.java**  add validation method for new values ([359bfff596ce0ce](https://github.com/bsorrentino/langgraph4j/commit/359bfff596ce0cefc39269b84b5fc052d2152086))
    > Added overridable method &#x60;validateNewValues&#x60; to ensure that the incoming list is compatible with the channel&#x27;s type parameter.

 -  **AppenderChannel**  expose nested class ReducerDisallowDuplicate and ReducerAllowDuplicate ([f127da2e2e9cc92](https://github.com/bsorrentino/langgraph4j/commit/f127da2e2e9cc9260bcd583fc935d9d3da4f80e2))
   
 -  **sprinhai-agentexecutor**  remove unused dependency on AgentExecutor from DemoConsoleController ([bc77b918e2637cc](https://github.com/bsorrentino/langgraph4j/commit/bc77b918e2637cc1bf3e7de793d2f9b6915ca72b))
    > Refactored AgentExecutor to an interface and static methods to improve modularity and decoupling.


### ALM 

 -  bump to version 1.5.5 ([4fcfd3ff1d3fc87](https://github.com/bsorrentino/langgraph4j/commit/4fcfd3ff1d3fc8755f0f43f004f8089e2e45a654))
   
 -  update `spring-ai` version to `1.0.0-M6` ([1284a997bca17a5](https://github.com/bsorrentino/langgraph4j/commit/1284a997bca17a5c11aa3ece3894ad3efd612330))
   
 -  update `spring-ai` version to `1.0.0-M6` ([f0ed8133b03cadd](https://github.com/bsorrentino/langgraph4j/commit/f0ed8133b03cadd4206cb60a702867853ce22cd7))
   
 -  bump to new SNAPSHOT ([f3973e48746f939](https://github.com/bsorrentino/langgraph4j/commit/f3973e48746f939e826228906aa7cb153f5aad18))
   






<!-- "name: v1.5.4" is a release tag -->

## [v1.5.4](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.4) (2025-04-06)

### Features

 *  **springai-agentexecutor**  add ChatService interface to improve configurability ([5a84f4d1035bae4](https://github.com/bsorrentino/langgraph4j/commit/5a84f4d1035bae4ff833ab236bc83046bc2a06ef))
   
 *  **spring-ai**  add module with Spring AI  utilities ([345e3058568820e](https://github.com/bsorrentino/langgraph4j/commit/345e3058568820e218d2ff8c119fcd2b45f2fba9))
   
 *  **spring-ai**  add module with Spring AI  utilities ([0d2c49f23440b9c](https://github.com/bsorrentino/langgraph4j/commit/0d2c49f23440b9c1522f9f03007f5f82a9d882b3))
     > - add serializers
     > - ToolService (to manage tools and their executor)
   


### Documentation

 -  bump to version 1.5.4 ([a6b6a511f637ce1](https://github.com/bsorrentino/langgraph4j/commit/a6b6a511f637ce1af7cf2b7ff1ab7684ab39ce94))

 -  **agent-executor**  improve code snippet ([dfaf18b52727f09](https://github.com/bsorrentino/langgraph4j/commit/dfaf18b52727f09746232f1796722ea10f9a5ed5))

 -  added README for LangGraph4j utilities integration with Spring AI ([a3b59ddb57c0cfc](https://github.com/bsorrentino/langgraph4j/commit/a3b59ddb57c0cfc4c6c9499f35023eac82eaff59))
     > Documentation was added to outline the integration of LangGraph4j utilities with Spring AI, including details on serializers, tools, and Maven dependency information.

 -  fix relase date ([36622302338c968](https://github.com/bsorrentino/langgraph4j/commit/36622302338c968ec2926f0e71d6f4f6009ab7e3))

 -  update changeme ([9c3f211924b2d30](https://github.com/bsorrentino/langgraph4j/commit/9c3f211924b2d3075429036f38c8cd0defdb67ea))


### Refactor

 -  **langchain4j**  deprecated classes in favor of meaningful ones ([02bd938c9ed8161](https://github.com/bsorrentino/langgraph4j/commit/02bd938c9ed81619ac1248048886effadb6e5d8f))
    > - JacksonMessagesStateSerializer -&gt; LC4jJacksonStateSerializer
 > - STDStateSerializer -&gt; LC4jStateSerialize
 > - ToolNode -&gt; LC4jToolService

 -  **springai-agent**  explicit create LLM Chat models ([9588b0d1f3bda28](https://github.com/bsorrentino/langgraph4j/commit/9588b0d1f3bda28dbf2379515c06f5e5654fcfd9))
   
 -  **spring-ai**  Rename ToolService to SpringAIToolService. ([e12381cbf4233cf](https://github.com/bsorrentino/langgraph4j/commit/e12381cbf4233cf19c5f40fa458eb60a4e021c0c))
   
 -  **agent-executor**  Deprecate ToolNode to LC4jToolService. ([20fa00807c4f3f8](https://github.com/bsorrentino/langgraph4j/commit/20fa00807c4f3f81910910641a394bd61df797ec))
   
 -  **springai-agentexecutor**  upgrade spring ai dependency and update code accordly ([a7131cba769e34d](https://github.com/bsorrentino/langgraph4j/commit/a7131cba769e34d2e45b83a0742d30e9459466d0))
   

### ALM 

 -  bump to version 1.5.4 ([447a11291bcdb5e](https://github.com/bsorrentino/langgraph4j/commit/447a11291bcdb5e477f64fe2e4c8463bd931f515))
   
 -  bump to new SNAPSHOT ([f81153f0c74d44b](https://github.com/bsorrentino/langgraph4j/commit/f81153f0c74d44bebb0bef7fe885b0ce9eef5ad0))
   






<!-- "name: v1.5.3" is a release tag -->

## [v1.5.3](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.3) (2025-04-03)

### Features

 *  **how-to**  update parallel-branch notebook ([1e740ef5cafa022](https://github.com/bsorrentino/langgraph4j/commit/1e740ef5cafa0226cbc6401c071decf30e842f46))
     > work on #104
   
 *  add support for compiled graph in parallel branch ([a05fa5479296373](https://github.com/bsorrentino/langgraph4j/commit/a05fa54792963730986072ef7989e7b3bdf1ed55))
     > - Refactored &#x60;getEmbedGenerator&#x60; to correcly merge result states
     > - Removed generator entry before updating currentState.
     > - Simplified state updating logic in &#x60;ParallelNode&#x60;.
     > - Added unique identifier for subgraph in state
     > - Updated tests for parallel branches in MessagesStateGraph workflow with multiple compiled subgraphs.
     > resolve #104
   
 *  **core/CollectionsUtils.java**  add methods for merging maps with conflict resolution ([e8acd24e7235717](https://github.com/bsorrentino/langgraph4j/commit/e8acd24e7235717bf6d6d95ac4d724824bffb5ac))
   
 *  add new example ([ad944f7658663f1](https://github.com/bsorrentino/langgraph4j/commit/ad944f7658663f16e4e41f63be2faed7a4c9494d))
     > work on #104
   

### Bug Fixes

 -  **checkpoints**  replace HashMap with ConcurrentHashMap for thread safety ([70dcb2397f56a44](https://github.com/bsorrentino/langgraph4j/commit/70dcb2397f56a4413b722965992281a1b316d6c0))
     > - Updated &#x60;_checkpointsByThread&#x60; from &#x60;HashMap&#x60; to &#x60;ConcurrentHashMap&#x60; to ensure thread safety during concurrent execution.
     > - Added test &#x60;Issue105Test&#x60; to verify that all threads are accounted for in &#x60;_checkpointsByThread&#x60;.
     > resolve #105


### Documentation

 -  bump to next release 1.5.3 ([66d5d54e4ea4f2a](https://github.com/bsorrentino/langgraph4j/commit/66d5d54e4ea4f2a1df7dd30224961c42f2924472))

 -  **how-to**  update notebook markdown documents ([5062d370939c0e1](https://github.com/bsorrentino/langgraph4j/commit/5062d370939c0e1f095126950cf07e86a5fe0d65))

 -  **README.md**  add project links to README ([0a90a39da400627](https://github.com/bsorrentino/langgraph4j/commit/0a90a39da40062788c4cf542584e850383117c3e))

 -  update changeme ([7dc20416d3245f1](https://github.com/bsorrentino/langgraph4j/commit/7dc20416d3245f1424ba044738d75ebe1b07aaa2))


### Refactor

 -  **core/MessagesState.java**  remove unused import ([e3e19cd4a0b230e](https://github.com/bsorrentino/langgraph4j/commit/e3e19cd4a0b230e66bbbbba2e01da25cf2bb6ddb))
   

### ALM 

 -  bump to next release 1.5.3 ([083263c7ad390b7](https://github.com/bsorrentino/langgraph4j/commit/083263c7ad390b71054dccbf2e8d5e114830c524))
   
 -  bump to new SNAPSHOT ([fbd1424e3eec36d](https://github.com/bsorrentino/langgraph4j/commit/fbd1424e3eec36d4fa9e74bd847978ba2e342611))
   






<!-- "name: v1.5.2" is a release tag -->

## [v1.5.2](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.2) (2025-04-01)


### Bug Fixes

 -  Properly handle state updates when return from a nested generator (eg. subgraph, llm-streaming) ([86ad6f88f42416e](https://github.com/bsorrentino/langgraph4j/commit/86ad6f88f42416e8a5b733e15b3feb6231c59bae))
     > - Makes Appender channel duplicate safe. !! WARNING THIS BREAK IN COMPATIBILITY !! may have undesirable effects
     > - deprecated .of() methods on AppenderChannel and Channel adding Channels interface with appender and base channel implementations
     > - Added tests for nested generator with stream, subgraph, and subgraph with appender
     > resolve #102

 -  **AgentExecutorAzureOpenAITest.java**  add required environment variables for Azure OpenAI configuration ([a86c922c98247ca](https://github.com/bsorrentino/langgraph4j/commit/a86c922c98247cad046cc04762bc5dd22873d343))
     > - Updated endpoint and deployment configuration in &#x60;AgentExecutorAzureOpenAITest&#x60; class to use environment variables: &#x60;AZURE_OPENAI_ENDPOINT&#x60;, &#x60;AZURE_OPENAI_DEPLOYMENT_NAME&#x60;.


### Documentation

 -  bump to version 1.5.2 ([0ce12b521078c9f](https://github.com/bsorrentino/langgraph4j/commit/0ce12b521078c9f2d228ad3de759f8de76c0426b))

 -  remove unused imports and update documentation ([411338a3ba9a2ef](https://github.com/bsorrentino/langgraph4j/commit/411338a3ba9a2ef4f274b899d67414a48bbaba65))
     > - Removed the AppenderChannel import from persistence.md
     > - Refactored Channel instantiation in MessagesState to use Channels.appender()
     > - Added link to Generator (Visual Builder) for LangGraph

 -  update changeme ([c4a69aa42f15532](https://github.com/bsorrentino/langgraph4j/commit/c4a69aa42f15532b9a44404cf6cc81dd56ad7ec2))


### Refactor

 -  remove lombok usage ([9bc154c5fd38681](https://github.com/bsorrentino/langgraph4j/commit/9bc154c5fd38681f7cf5bc3a4b7ada179a5982bd))
    > resolve #103

 -  **how-tos**  remove unused imports ([226505cd2a34f6f](https://github.com/bsorrentino/langgraph4j/commit/226505cd2a34f6f98d329028f9040aec1601f8de))
   
 -  replace deprecated methods with right ones ([8c31f359f9f85a6](https://github.com/bsorrentino/langgraph4j/commit/8c31f359f9f85a6a6d41358ef33ea21f20b8e221))
   

### ALM 

 -  bump to version 1.5.2 ([56a01f125ca360f](https://github.com/bsorrentino/langgraph4j/commit/56a01f125ca360fd25020aad714038ab4cdca5eb))
   






<!-- "name: v1.5.1" is a release tag -->

## [v1.5.1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.1) (2025-03-28)

### Features

 *  **how-tos**  add new notebook for verify issue 99 ([4f8d6bded777cb5](https://github.com/bsorrentino/langgraph4j/commit/4f8d6bded777cb5cb9950f9100977140540f7434))
     > work on #99
   

### Bug Fixes

 -  **CompiledGraph.java**  fix interruption before node after start ([813938d6802eedc](https://github.com/bsorrentino/langgraph4j/commit/813938d6802eedc6c3f3e863f000b5229544f0b7))
     > Ensure that the compiled graph checks if the current node ID is the same as the previous node ID before interrupting.
     > work on #100

 -  **CompiledGraph.java**  update edge retrieval logic for subgraph processing ([9cf33ccfb892bf9](https://github.com/bsorrentino/langgraph4j/commit/9cf33ccfb892bf9ac4db02b09ad3ef3ad0c01d8a))
     > - Replaced &#x60;stateGraph.edges.edgesByTargetId(subgraphNode.id())&#x60; with &#x60;edges.edgesByTargetId(subgraphNode.id())&#x60;
     > - Replaced &#x60;stateGraph.edges.edgeBySourceId(subgraphNode.id()).orElseThrow()&#x60; with &#x60;edges.edgeBySourceId(subgraphNode.id()).orElseThrow()&#x60;
     > These changes fix problem when process multiple subgraph.
     > work on #99

 -  **generator**  update gif url ([fcdc75a871962bb](https://github.com/bsorrentino/langgraph4j/commit/fcdc75a871962bb73ad1e94080a666bcbc515fb9))

 -  **generator**  readme typo ([cc1761f50dfa9ef](https://github.com/bsorrentino/langgraph4j/commit/cc1761f50dfa9ef13c30d43b2e96ceeb069554f5))


### Documentation

 -  bump to next release 1.5.1 ([114e36023e9a6c4](https://github.com/bsorrentino/langgraph4j/commit/114e36023e9a6c4cee3909b2f7987bcd95d99f1d))

 -  bump to new SNAPSHOT in badge ([c0f05b2b4b92981](https://github.com/bsorrentino/langgraph4j/commit/c0f05b2b4b92981489ec493844effff191dea33c))

 -  update changeme ([0dc31cdf85e2bb2](https://github.com/bsorrentino/langgraph4j/commit/0dc31cdf85e2bb204703a73cb451e1b2778095fb))


### Refactor

 -  **AgentExecutor**  rename builder ([0a7907bdc802404](https://github.com/bsorrentino/langgraph4j/commit/0a7907bdc802404647e70ecac85cb3bcefad4235))
    > - Renamed &#x60;GraphBuilder&#x60; to &#x60;Builder&#x60;.
 > - Introduced static factory methods &#x60;builder()&#x60; and &#x60;graphBuilder()&#x60;, deprecating the old names.
 > - Refactored AgentExecutorTest to use updated builder method.


### ALM 

 -  bump to next release 1.5.1 ([cfe285e77864c77](https://github.com/bsorrentino/langgraph4j/commit/cfe285e77864c775fec41c01c233acfca0d4fbc5))
   
 -  bump to new SNAPSHOT ([f43c753c569f344](https://github.com/bsorrentino/langgraph4j/commit/f43c753c569f344502e011521b3ffad9e099c8d5))
   
 -  **how-tos**  bump to new SNAPSHOT ([36211f6549faa2a](https://github.com/bsorrentino/langgraph4j/commit/36211f6549faa2a2e7e039a744810ec04a374f44))
   

### Test 

 -  include test for check intrruptions ssenarios also on subgraph ([65d6c1a0889eb0f](https://github.com/bsorrentino/langgraph4j/commit/65d6c1a0889eb0fd7c74d1ca99cbc34dae7bb4e8))
    > work on #100

 -  add unit tests for check edge processing in multiple graphs scenario ([ea3263158137585](https://github.com/bsorrentino/langgraph4j/commit/ea3263158137585e8792ae0413de674000d231c7))
    > work on #99






<!-- "name: v1.5.0" is a release tag -->

## [v1.5.0](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.5.0) (2025-03-25)

### Features

 *  **generator**  package builder and generator in docker ([ae8f57fb029d60e](https://github.com/bsorrentino/langgraph4j/commit/ae8f57fb029d60ea8037807ce0b3565f9fab3a42))
     > work on #97
   
 *  **builder-webui**  add builder UI for visually design the graph. ([97f615f0f8563fa](https://github.com/bsorrentino/langgraph4j/commit/97f615f0f8563fa08b20f41e555438733f97d0e2))
     > thi project is based on a fork of  https://github.com/langchain-ai/langgraph-builder
     > work on #97
   
 *  **Generator**  Add generator implementation ([b42db819c334dc8](https://github.com/bsorrentino/langgraph4j/commit/b42db819c334dc802e6f07afcbf0c977cf6c2a98))
     > - add the Generator class that enables the creation of custom agent builder stub and sample implementation using mustache templates
     > - add The GraphDefinition class that represent the graph model
     > - add related  unit test
     > work on #97
   
 *  **studio/webui/**  refactor execution logic ([434a7e58507661d](https://github.com/bsorrentino/langgraph4j/commit/434a7e58507661d3501038b6afdb2900b22f653c))
     > - Extracted start and stop execution logic into separate methods
     > - Added custom events to notify the state changes (start, interrupted, stop) during execution.
     > work on #74
   
 *  **studio/webui**  add message display and state management for graph interaction ([9a805c27425f55c](https://github.com/bsorrentino/langgraph4j/commit/9a805c27425f55cde466281e4c95573e24af188f))
     > - Added methods &#x60;#writeMessage&#x60; to update a message element in the UI.
     > - Created handlers &#x60;#onGraphActive&#x60; and &#x60;#onStateUpdated&#x60; to manage &#x27;graph-active&#x27; and &#x27;state-updated&#x27; events.
     > - Updated event listeners to use these new handler methods.
     > - Modified the navbar in lg4j-workbench.js to include elements for displaying messages (&#x60;#message&#x60;) and state indicators (&#x60;#spinner&#x60;).
     > - Ensured proper cleanup of event listeners on disconnection.
     > work on #74
   
 *  **studio/base**  add support for JacksonStateSerializer ([b65dc92e383dad1](https://github.com/bsorrentino/langgraph4j/commit/b65dc92e383dad1aeacbbaa4ed00796f5c254ff3))
     > - Introduced &#x60;JacksonStateSerializer&#x60; to handle JSON serialization.
     > - Updated the &#x60;LangGraphStreamingServer&#x60; to recognize and utilize the &#x60;JacksonStateSerializer&#x60; if present.
     > - Adjusted logic to correctly configure the &#x60;objectMapper&#x60; based on the state serializer type.
     > - Modified the state update and resumption logic to accommodate the new serializer.
     > work on #74
   
 *  Jackson based serialization refinements ([b7eb009e9a65002](https://github.com/bsorrentino/langgraph4j/commit/b7eb009e9a650025866656b1f8cd835ff9c40b18))
     > - Add TypeMapper to map class  type with type name
     > - Provide implementation for GenericListDeserializer and GenericMapDeserializer
     > - Unit test for Jackson based serialization
   
 *  **CollectionsUtils.java**  add method to merge two maps ([395f5cd481e471f](https://github.com/bsorrentino/langgraph4j/commit/395f5cd481e471f04fa0be29664c611bcbdfe011))
   
 *  add support for JSON serialization using Jackson for langchain4j Chat Messages ([44eb32cb5c953f6](https://github.com/bsorrentino/langgraph4j/commit/44eb32cb5c953f6db6b39db8d59f4c364c16a41b))
   
 *  update jetty,springboot,quarkus implementation ([9d75b9faff8afcd](https://github.com/bsorrentino/langgraph4j/commit/9d75b9faff8afcdec40fd2a6b23d57ad340439dd))
     > - provide CompileConfig
     > - update webui
     > work on #74
   
 *  **LangGraphStreamingServer.java**  add support to provide CompileConfig to streaming servlet ([3cce4301bba314e](https://github.com/bsorrentino/langgraph4j/commit/3cce4301bba314e838c9bf4725410040ee93281c))
     > - Removed unused import &#x60;BaseCheckpointSaver&#x60;
     > - Updated constructor parameters to be more descriptive (&#x60;stateGraph&#x60;, &#x60;compileConfig&#x60;)
     > - Removed redundant checkpoint saving logic in &#x60;compileConfig&#x60; method
     > work on 74
   
 *  **studio/webui**  detect interruption ([d77a841c2dba2a6](https://github.com/bsorrentino/langgraph4j/commit/d77a841c2dba2a6a912a5c7a7729cc3c19128c51))
     > update script for deployment to jetty,springboot,quarkus
     > work on #74
   

### Bug Fixes

 -  **studio/webui**  highlight the next node instead of current one ([da251d4cd6bd55b](https://github.com/bsorrentino/langgraph4j/commit/da251d4cd6bd55b16a0948bf62c5d294e8c2e4bc))
     > work on #74


### Documentation

 -  bump to new SNAPSHOT ([38d4f4b8766ea9d](https://github.com/bsorrentino/langgraph4j/commit/38d4f4b8766ea9dc7dc6a4167ca412554c1589dc))

 -  (generator) add README and Maven Site ([79321559738a800](https://github.com/bsorrentino/langgraph4j/commit/79321559738a800d87fcd6891b5fc931d0f7ebd5))
     > work on #97

 -  update changeme ([a286678d3d1818c](https://github.com/bsorrentino/langgraph4j/commit/a286678d3d1818c1134898d358f03975ec2eba20))


### Refactor

 -  **core**  remove deprecated marked for removal ([55a880b5009ab10](https://github.com/bsorrentino/langgraph4j/commit/55a880b5009ab1020a3bb83693ce25d2f4f4ed5c))
    > Removed:
 > - StateGraph.getEntryPoint()
 > - StateGraph.getFinishPoint()
 > - StateGraph.setEntryPoint()
 > - StateGraph.setFinishPoint()
 > - StateGraph.setConditionalEntryPoint()
 > - AgentState.AppendableValue()
 > - class  AppendableValue {}
 > - class  AppendableValueRW {}
 > work on #96

 -  **CompiledGraph.java**  refine node output construction ([83e713246fd7a9a](https://github.com/bsorrentino/langgraph4j/commit/83e713246fd7a9aaac470577c18359935117c5cc))
    > Refactors the &#x60;streamSnapshots&#x60; to return the __START__ completion as StateSnaphot instead of NodeOutput
 > work on #74

 -  **studio/webui**  handle JSON streaming responses efficiently ([ba48bf83cabdaf6](https://github.com/bsorrentino/langgraph4j/commit/ba48bf83cabdaf6d437907156dacdd3f1ddcc892))
    > - Updated &#x60;streamingResponse&#x60; to decode chunks into a buffer and then parse JSON, ensuring only complete messages are emitted.
 > - Removed unused variable &#x60;interrupted&#x60; as logic has been updated.
 > - Adjusted dispatch event&#x27;s detail property to use the parsed JSON directly.
 > work on #74

 -  **AppenderChannel.java**  change constructor access level to protected ([27beeea5a45808a](https://github.com/bsorrentino/langgraph4j/commit/27beeea5a45808a72aa688f88afd34d4f1c089a3))
    > - allow AppenderChannel specialization


### ALM 

 -  **how-tos**  bump to new SNAPSHOT ([0dc54f9857be40d](https://github.com/bsorrentino/langgraph4j/commit/0dc54f9857be40deb009a70ad9d9f0060c2327e1))
   
 -  bump to version 1.5.0 ([259ccf45771a802](https://github.com/bsorrentino/langgraph4j/commit/259ccf45771a80294271b80afffe2ab081c2549f))
   
 -  **generator**  add new maven  module project ([0bedceea3064be1](https://github.com/bsorrentino/langgraph4j/commit/0bedceea3064be18d9476f3e3a6282b2a958ef63))
    > work on #97

 -  **pom.xml**  Add new module `generator` to project ([7d380e4f80300fc](https://github.com/bsorrentino/langgraph4j/commit/7d380e4f80300fc0074bf11cf958a3f23118f193))
    > work on #97

 -  **studio/webui**  update distribution ([3852737e640f965](https://github.com/bsorrentino/langgraph4j/commit/3852737e640f9657410d9246cde824672719d09e))
   
 -  **studio**  update webui distribution ([34d02f3a7d5ac60](https://github.com/bsorrentino/langgraph4j/commit/34d02f3a7d5ac603e1a5b2354e46916053df7150))
    > work on #74

 -  **studio/webui**  update studio distribution ([5b3cb334379734c](https://github.com/bsorrentino/langgraph4j/commit/5b3cb334379734c000635e7adbb50d5f3ce4f264))
    > work on #74

 -  bump to 1.5-SNAPSHOT ([0c28535c4582f93](https://github.com/bsorrentino/langgraph4j/commit/0c28535c4582f93d7d885e12b7b8442fcbe7817c))
   

### Test 

 -  **studio/webui/**  refactor execution logic ([fe9203bc5efe6c4](https://github.com/bsorrentino/langgraph4j/commit/fe9203bc5efe6c40f4fc7eef14f6291e83461f36))
    > - Extracted start and stop execution logic into separate methods
 > - Added custom events to notify the state changes (start, interrupted, stop) during execution.
 > work on #74

 -  add support for JSON serialization using Jackson for langchain4j Chat Messages ([3e4b183a397c57b](https://github.com/bsorrentino/langgraph4j/commit/3e4b183a397c57ba3de9db64b55fdd2c9b468bda))
   





<!-- "name: v1.4.4-patch1" is a release tag -->

## [v1.4.4-patch1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.4.4-patch1) (2025-03-21)



### Documentation

 -  update changeme ([c9f59de263e7bdc](https://github.com/bsorrentino/langgraph4j/commit/c9f59de263e7bdc521a6dba7e12874cb2c642855))









<!-- "name: v1.4.4" is a release tag -->

## [v1.4.4](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.4.4) (2025-03-21)

### Features

 *  add support for JSON serialization using Jackson for langchain4j Chat Messages ([cf8943d42dca955](https://github.com/bsorrentino/langgraph4j/commit/cf8943d42dca95593bac32860035d88465258f94))
   
 *  Jackson based serialization refinements ([8f06f76e9f643f0](https://github.com/bsorrentino/langgraph4j/commit/8f06f76e9f643f00e27a4d132329db26ddab874d))
     > - Add TypeMapper to map class  type with type name
     > - Provide implementation for GenericListDeserializer and GenericMapDeserializer
     > - Unit test for Jackson based serialization
   
 *  Jackson based serialization refinements ([946588148e58297](https://github.com/bsorrentino/langgraph4j/commit/946588148e582978835afbc7d28fc7a2856e009b))
     > - Add TypeMapper to map class  type with type name
     > - Provide implementation for GenericListDeserializer and GenericMapDeserializer
     > - Unit test for Jackson based serialization
   
 *  **langchain4j**  Adds a new class for integration of chat memory  within graph state ([8174df0586d5616](https://github.com/bsorrentino/langgraph4j/commit/8174df0586d5616e18723d86f193f7481c09d9aa))
     > - Introduce &#x60;GraphChatMemory&#x60; class to manage conversation histories using graph state. This allows a seamless experience in using langchain4j chat memory implementation in langgraph4j.
   


### Documentation

 -  bump to 1.4.4 release ([73841494cd75a86](https://github.com/bsorrentino/langgraph4j/commit/73841494cd75a86b63d7b27db5cbf49eadfad033))

 -  update changeme ([e81907b5c6482e4](https://github.com/bsorrentino/langgraph4j/commit/e81907b5c6482e4bcb049e7d336a5b2e34d88cf3))


### Refactor

 -  **AppenderChannel.java**  change constructor access level to protected ([7245120f3bfdb1d](https://github.com/bsorrentino/langgraph4j/commit/7245120f3bfdb1df811ffc05a26e7c068c2564cb))
    > - allow AppenderChannel specialization


### ALM 

 -  bump to 1.4.4 release ([56835ab018ab7bd](https://github.com/bsorrentino/langgraph4j/commit/56835ab018ab7bdbbdc34537ec83a57226394436))
   
 -  bump to SNAPSHOT ([167ace3002c162b](https://github.com/bsorrentino/langgraph4j/commit/167ace3002c162b25f07f5f0d4ee7becb4045c22))
   






<!-- "name: v1.4.3" is a release tag -->

## [v1.4.3](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.4.3) (2025-03-15)

### Features

 *  **core**  Add utility class for edge mappings in state graph ([68012fb87133a46](https://github.com/bsorrentino/langgraph4j/commit/68012fb87133a463bc8e20d95acec056a1db8669))
     > EdgeMappings class provides a fluent API to create immutable maps representing edges between states in a state graph. This utility makes it easier to define and configure state transitions, ensuring consistency and reducing errors.
   
 *  **AgentState.java**  Add functionality to handle null values in state operations for their removal ([3e591a2bf41da0e](https://github.com/bsorrentino/langgraph4j/commit/3e591a2bf41da0e9942dcaa881eea8115c77a29c))
     > Introducing in &#x60;AgentState&#x60; class two new utility methods: &#x60;toMapAllowingNulls&#x60; and &#x60;toMapRemovingNulls&#x60;.
   

### Bug Fixes

 -  remove usage of deprecated listOf() ([14c28801eeb137e](https://github.com/bsorrentino/langgraph4j/commit/14c28801eeb137e37bd2378e21ae3245d9c6f333))

 -  remove usage of deprecated mapOf() ([45064fbfedd709b](https://github.com/bsorrentino/langgraph4j/commit/45064fbfedd709b78365ca3d273d1bd3af1b4814))

 -  remove usage of deprecated addSubgraph() ([70226bdc2a792c4](https://github.com/bsorrentino/langgraph4j/commit/70226bdc2a792c40f076d91780e3d667d0ed78e9))

 -  remove usage of deprecated addSubgraph() ([3762b23e4e98e0b](https://github.com/bsorrentino/langgraph4j/commit/3762b23e4e98e0b69dab065fc5b4bfe1fb5a7de1))


### Documentation

 -  bump to version 1.4.3 ([3649144307807c8](https://github.com/bsorrentino/langgraph4j/commit/3649144307807c8ead740429a5fe10e5731529e4))

 -  update changeme ([e9899681299a948](https://github.com/bsorrentino/langgraph4j/commit/e9899681299a94852012293eb6a5edfba4732358))


### Refactor

 -  upgrade langchain4j version to 1.0.0-beta2 and update affected code accordingly ([858bc8318acde90](https://github.com/bsorrentino/langgraph4j/commit/858bc8318acde90f73626814027be21ab511dc85))
   
 -  **StateGraph**  add new methods to add subgraphs as nodes ([1ee7bdf6086816c](https://github.com/bsorrentino/langgraph4j/commit/1ee7bdf6086816c8a0639a4a4d50daa92ece3c62))
    > - Introduced a new method &#x60;addNode&#x60; that allows adding a subgraph to the state graph by creating a node with the specified identifier. This method replaces and deprecates the existing &#x60;addSubgraph&#x60; method.


### ALM 

 -  bump to version 1.4.3 ([bf31cd481591313](https://github.com/bsorrentino/langgraph4j/commit/bf31cd48159131338f9556601738b723cdd9078a))
   
 -  bump to SNAPSHOT ([0538288b35db864](https://github.com/bsorrentino/langgraph4j/commit/0538288b35db864affa12095ff5f1a6dafe987ee))
   

### Test 

 -  refine graph representation generation using EdgeMappings ([d865a766d591163](https://github.com/bsorrentino/langgraph4j/commit/d865a766d5911635ed8efa9b363e9b6750f46c7f))
   
 -  **AgentState**  test the state's value removal using nul ([f506a9b8e1df0e0](https://github.com/bsorrentino/langgraph4j/commit/f506a9b8e1df0e083e82df226f39e1002dbab40a))
   





<!-- "name: v1.4.2" is a release tag -->

## [v1.4.2](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.4.2) (2025-03-11)


### Bug Fixes

 -  **langchain4j/serializer**  handle nullable values in ToolExecutionRequest and ToolExecutionResultMessage serializers ([9b686e6281f9753](https://github.com/bsorrentino/langgraph4j/commit/9b686e6281f975330df84cf7c62ebb84b2461aae))
     > This commit introduces a new interface &#x60;NullableObjectSerializer&#x60; that extends &#x60;Serializer&#x60; to handle nullable objects during serialization, adding logic to manage null id values
     > resolve #93

 -  **pom.xml**  update langgraph4j-parent version to 1.4.1 ([5687f029a816daf](https://github.com/bsorrentino/langgraph4j/commit/5687f029a816daf6436c2cb85bc40684aff65ae9))


### Documentation

 -  bump to 1.4.2 ([160d7fce9a93170](https://github.com/bsorrentino/langgraph4j/commit/160d7fce9a9317075147ed7428652e32085b8b32))

 -  **notebook**  update head description ([38567a32037be34](https://github.com/bsorrentino/langgraph4j/commit/38567a32037be346282d21a7c486e64134dae755))

 -  **how-tos**  update site documentation ([bbe80306994e7b1](https://github.com/bsorrentino/langgraph4j/commit/bbe80306994e7b1f59952db4a6470b264b276777))
     > - update &#x27;multi-agent supervisor&#x27;

 -  **how-tos**  update site documentation ([5c2d9ea0fb7a549](https://github.com/bsorrentino/langgraph4j/commit/5c2d9ea0fb7a549c73c4c473a560d6adab152457))
     > - add &#x27;multi-agent supervisor&#x27;

 -  **agent-executor**  update site ([262db50327700ec](https://github.com/bsorrentino/langgraph4j/commit/262db50327700ec0f33851897d222fa62b4f0ce0))
     > - fix code sample

 -  update readme.md ([1412671d5a78553](https://github.com/bsorrentino/langgraph4j/commit/1412671d5a78553bf9d8303df5d14f241a299ccd))
     > fix snapshots link

 -  List Quarkus under Studio & fix minor typo. ([f9de69a1b0193f6](https://github.com/bsorrentino/langgraph4j/commit/f9de69a1b0193f64b6f7f75d8d1fd9b07e5c0a87))
     > merge PR #83

 -  update changeme ([94b899d5abe2270](https://github.com/bsorrentino/langgraph4j/commit/94b899d5abe2270243401f11df8fd1f9d112764d))


### Refactor

 -  **tool/toolnode**  update implementation with record syntax ([279e38e3876edff](https://github.com/bsorrentino/langgraph4j/commit/279e38e3876edffd24451ce039ce747f86a09690))
   
 -  **serializer**  add logging for null ids in ToolExecutionRequestSerializer and ToolExecutionResultMessageSerializer ([27aa1d7cee4159e](https://github.com/bsorrentino/langgraph4j/commit/27aa1d7cee4159ec257939e492fe9b9af5086354))
    > This refactoring adds logging to serialize methods in &#x60;ToolExecutionRequestSerializer&#x60; and &#x60;ToolExecutionResultMessageSerializer&#x60;. The logs will warn if the id is null, aiding in debugging potential issues where null ids are not handled gracefully.
 > work on #93

 -  **how-tos**  wait-user-input refinemnts ([3eb7b1544b9f880](https://github.com/bsorrentino/langgraph4j/commit/3eb7b1544b9f8804b035237ef0134a6b336cb692))
    > - add conditional edges to handle user input
 > resolve discussion #84

 -  **notebook**  refine 'Multi-agent supervisor' implementation ([c9d9994548c47fd](https://github.com/bsorrentino/langgraph4j/commit/c9d9994548c47fd31dd1a3e0dd398c1aa41ca1c0))
    > - use NodeAction rather than AsyncNodeAction


### ALM 

 -  bump to 1.4.2 ([34a94c6714ca996](https://github.com/bsorrentino/langgraph4j/commit/34a94c6714ca99667e91d7277c183d12890c216e))
   
 -  **langchain4jl**  upgrade Java compiler version to 17 ([a71ca59e9755e0a](https://github.com/bsorrentino/langgraph4j/commit/a71ca59e9755e0a1755bfeab4a2e84859d46c849))
   
 -  bump to SNAPSHOT ([7a6d489d230829b](https://github.com/bsorrentino/langgraph4j/commit/7a6d489d230829b926fc590628d91bb99ba3d1dd))
   

### Test 

 -  add notebook for replicating the issue #93 ([2d0867fd5fc2e08](https://github.com/bsorrentino/langgraph4j/commit/2d0867fd5fc2e08c38eb5dc819657282f46fd45c))
   





<!-- "name: v1.4.1" is a release tag -->

## [v1.4.1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.4.1) (2025-02-24)

### Features

 *  add new class for LLM streaming support ([4d8977fbf779471](https://github.com/bsorrentino/langgraph4j/commit/4d8977fbf7794718bd5283ee398e317bf589d21c))
     > - add StreamingChatGeneraror
     > - deprecate LLMStreaminGenerator
     > - update notebook
     > resolve #82
   
 *  **studio**  add support for Quarkus in Studio ([5321eb1f716299a](https://github.com/bsorrentino/langgraph4j/commit/5321eb1f716299aff9c235e1aa155781fcbc5425))
     > resolve #81
   

### Bug Fixes

 -  **site.xml**  update Studio menu item to correct base path ([16eb0bdf07e7ae1](https://github.com/bsorrentino/langgraph4j/commit/16eb0bdf07e7ae1a37c3e1264332569468c1d4a4))

 -  **changelog**  add 'test' support in changelog generation template ([74be5d31e6019e9](https://github.com/bsorrentino/langgraph4j/commit/74be5d31e6019e95b3df57c523407a04720a6572))


### Documentation

 -  bump to next release ([ee36919d3a25c4a](https://github.com/bsorrentino/langgraph4j/commit/ee36919d3a25c4a1ebbb53e112da521496db086c))

 -  update documentation ([87170d9dec82a3b](https://github.com/bsorrentino/langgraph4j/commit/87170d9dec82a3b8111eb07ec6d5516905fe21dd))
     > add more &#x27;how to&#x27;

 -  update changeme ([a8938bab782f499](https://github.com/bsorrentino/langgraph4j/commit/a8938bab782f4998dd2275750e218318671de9ed))


### Refactor

 -  **agent-executor**  update  agent-executor  implementation ([f8d11303458190b](https://github.com/bsorrentino/langgraph4j/commit/f8d11303458190bce1ffc9981b6d09df8bf7fd92))
    > - remove deprecated usage
 > - Simplify implementation using MessagesState

 -  **agent-executor**  update  agent-executor  implementation ([bb880109b2e5d58](https://github.com/bsorrentino/langgraph4j/commit/bb880109b2e5d580eb705be4ba5550cafea68991))
    > - remove deprecated usage
 > - Simplify implementation using MessagesState

 -  **studio/quarkus**  quarkus impl refinements ([317bf09a5958cc3](https://github.com/bsorrentino/langgraph4j/commit/317bf09a5958cc381e0d48635802edb25664cffb))
    > using of @Produces to inject flow
 > work on #81


### ALM 

 -  bump to next release ([20b62ce590197bc](https://github.com/bsorrentino/langgraph4j/commit/20b62ce590197bc6afcf72409ce4ba7196b7ea0d))
   
 -  move to next SNAPSHOT ([4250364d58d0ff9](https://github.com/bsorrentino/langgraph4j/commit/4250364d58d0ff9b95a19a922c06d78e0912b93f))
   






<!-- "name: v1.4.0" is a release tag -->

## [v1.4.0](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.4.0) (2025-02-17)

### Features

 *  **studio/LangGraphStreamingServer.java**  compile the state graph before extract graph representation to support subgraph merge . ([dbc2394340779aa](https://github.com/bsorrentino/langgraph4j/commit/dbc2394340779aa2e0c812bde905dab0876ebd49))
     > work on #73
   

### Bug Fixes

 -  **how-tos/pom.xml**  ensure up-to-date versions for langchain4j dependencies ([dcc434268363bb1](https://github.com/bsorrentino/langgraph4j/commit/dcc434268363bb15df85f40daee62077b448460b))

 -  align doc with new versions and links ([afd521d3deb51ca](https://github.com/bsorrentino/langgraph4j/commit/afd521d3deb51ca0b5ac07713bfc473a1598600c))


### Documentation

 -  bump ton next relase ([281c8bb37d469e2](https://github.com/bsorrentino/langgraph4j/commit/281c8bb37d469e2fd76c16b8dc4659a7c37a37b2))

 -  **how-tos**  update notebook documentation ([8ecb252c88b294b](https://github.com/bsorrentino/langgraph4j/commit/8ecb252c88b294b99079763257275b8a5c5e3b64))

 -  **how-to**  update links toward how-tos ([07574c854abf79e](https://github.com/bsorrentino/langgraph4j/commit/07574c854abf79ecdd4963a51f25d574cd748c3c))

 -  **how-to**  update links to include /langgraph4j prefix ([d14547298a463e6](https://github.com/bsorrentino/langgraph4j/commit/d14547298a463e6129562f054bcebca442c22bda))

 -  align documentation ([e213b96933e337d](https://github.com/bsorrentino/langgraph4j/commit/e213b96933e337da42a90b18a6801155c6a18bfc))
     > - fix links
     > - fix studio springboot javadoc adding package &#x27;pringboot&#x27;
     > - assign right names to site pages

 -  update changeme ([c5d8e914af2d506](https://github.com/bsorrentino/langgraph4j/commit/c5d8e914af2d506606f259214c940ccbb818c0e4))


### Refactor

 -  **howtos**  bump to last langchain4j version ([0e9d8a513298aa2](https://github.com/bsorrentino/langgraph4j/commit/0e9d8a513298aa24e634e2b561748f2c74cdb30d))
   
 -  **pom.xml**  update dependencies and versions ([f9e2c8d9335c37f](https://github.com/bsorrentino/langgraph4j/commit/f9e2c8d9335c37f832d372c328f51e98577afc7b))
    > - Updated Langchain4j dependencies across all projects to version 1.0.0-beta1
 > - Added Ollama dependency in the how-tos sub-project

 -  correct typo in MermaidGenerator and PlantUMLGenerator ([e19ca4395beee6f](https://github.com/bsorrentino/langgraph4j/commit/e19ca4395beee6f4885a85a9c5dbd40bec577c34))
    > - Fixed incorrect method name &#x27;isSubgraph&#x27; to &#x27;isSubGraph&#x27; across both classes.

 -  **DiagramGenerator.java**  fix field name inconsistencies ([321b54c99c8d9a1](https://github.com/bsorrentino/langgraph4j/commit/321b54c99c8d9a18f372da4b4aa122c38eb39c6c))
    > - Changed &#x27;isSubgraph&#x27; to &#x27;isSubGraph&#x27;
 > - Updated related method names to maintain consistency

 -  **GraphRepresentation.java**  update class structure using a record ([c074d6176bdcca4](https://github.com/bsorrentino/langgraph4j/commit/c074d6176bdcca41b077821fbd668bde4680de29))
    > - Updated &#x60;GraphRepresentation&#x60; to use Java&#x27;s &#x60;record&#x60; feature for better encapsulation.
 > - Deprecated the old fields and methods in favor of the new record components.
 > - Added documentation for improved clarity and future maintenance.

 -  **core/SubGraphNode.java**  update node identifier format ([886c9cc54c287ed](https://github.com/bsorrentino/langgraph4j/commit/886c9cc54c287ed95bfd36d75f406ab825afa374))
    > Updated the PREFIX_FORMAT to &quot;%s-%s&quot; and added methods for retrieving the unique identifier and formatting subgraph IDs.
 > - add the &#x60;id()&#x60; method to return the unique identifier for the node.
 > - Added a default implementation of &#x60;formatId(String nodeId)&#x60; that formats the given node ID with a predefined prefix.
 > - Refactored existing static method &#x60;formatId(String subGraphNodeId, String nodeId)&#x60; to use the new format.


### ALM 

 -  bump to next relase ([2be780df0a171da](https://github.com/bsorrentino/langgraph4j/commit/2be780df0a171daf4abb4268f48a7bd4e284a815))
   
 -  **studio/springboot**  add Lombok dependency for developer productivity ([eef9616cff424b4](https://github.com/bsorrentino/langgraph4j/commit/eef9616cff424b439996f1243adf0e75377efc31))
   

### Test 

 -  **how-tos**  add MultiAgentSupervisor test case ([3d5818a97884593](https://github.com/bsorrentino/langgraph4j/commit/3d5818a9788459327c74d21d488439e7480795a9))
    > work on #79

 -  **how-tos**  add MultiAgentSupervisor test case ([cfd5aaf91f3f8f1](https://github.com/bsorrentino/langgraph4j/commit/cfd5aaf91f3f8f12348d0aa0de9b78f6d3593925))
    > work on #79

 -  **studio/springboot**  test merge subgraph execution ([2a6baaa390977f3](https://github.com/bsorrentino/langgraph4j/commit/2a6baaa390977f32e4110b1a4f6cf9750f32850c))
    > work on #73






<!-- "name: v1.4.0-beta2" is a release tag -->

## [v1.4.0-beta2](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.4.0-beta2) (2025-02-12)

### Features

 *  **subgraph**  allow produce graph containing  subgraph representations both original (from state graph ) and merged  (from compile graph) ([db5dfb587902acc](https://github.com/bsorrentino/langgraph4j/commit/db5dfb587902acc98281e41882a281003138b402))
   
 *  **representation**  set dotted line for conditional edges ([c4e2f7d50840d07](https://github.com/bsorrentino/langgraph4j/commit/c4e2f7d50840d07fae1bfa653a1ab6784699474e))
   
 *  **CompiledGraph.java**  enhance subgraph processing in state graph ([4d61965829c6c0e](https://github.com/bsorrentino/langgraph4j/commit/4d61965829c6c0ef0ab320dc10285e97605aa641))
     > It ensures that interruptions (nodes marked as &quot;before&quot;) are correctly redirected to the real target ID after subgraph expansion.
     > work on #73
   
 *  **StateGraph.java**  Add error message for non-existent interruption node ([40cc8ed94ed5c20](https://github.com/bsorrentino/langgraph4j/commit/40cc8ed94ed5c2026cc7b9d92e2def3c22d9f6e9))
     > Added an error message to handle the scenario where a node configured as an interruption does not exist in the StateGraph.
   
 *  **CompiledGraph.java**  add interruption node checks ([345edf9dd9d31f9](https://github.com/bsorrentino/langgraph4j/commit/345edf9dd9d31f95aad59e91700046ebe57b8d55))
     > Added checks for interruption nodes before processing state graph nodes and edges.
   
 *  **CompiledGraph.java**  enhance subgraph processing in state graph ([15213935aa39e03](https://github.com/bsorrentino/langgraph4j/commit/15213935aa39e03d48b8867846b05895f2fe18cc))
     > It ensures that interruptions (nodes marked as &quot;before&quot;) are correctly redirected to the real target ID after subgraph expansion.
     > work on #73
   
 *  **SubGraphNode.java**  introduce SubGraphNode interface ([5ef408ca9d5987a](https://github.com/bsorrentino/langgraph4j/commit/5ef408ca9d5987a3cb536d597d6e76dcf69f0cc9))
     > Add a new interface &#x60;SubGraphNode&#x60; in package &#x60;org.bsc.langgraph4j&#x60;, which defines methods related to managing sub-graph nodes with specific states.
     > This interface includes a method for obtaining the sub-graph and a static utility method for formatting node IDs to distinct subgraph nodes from standard ones
     > work on #73
   
 *  **CompiledGraph.java**  Refactor `CompiledGraph` to prepare for support of sub-graphs merge ([1e588777e6c1419](https://github.com/bsorrentino/langgraph4j/commit/1e588777e6c14195248685b8cc0a797569c355d0))
     > - Removed deprecated methods &#x60;getEntryPoint&#x60; and &#x60;getFinishPoint&#x60;
     > work on #73
   

### Bug Fixes

 -  **site**  rename folder studio/core to studio/base ([0c27a6bbd36ede1](https://github.com/bsorrentino/langgraph4j/commit/0c27a6bbd36ede1e24ee9aaddfbbc1648ab5cc11))

 -  **Node.java**  Ensured the `equals` method only casts to `Node<?>` if the object is not null, ([69fb19709efa6a6](https://github.com/bsorrentino/langgraph4j/commit/69fb19709efa6a6e50de093077528e7aa81ee78b))
     > work on #73


### Documentation

 -  bump version ([aa9d0d0881aff06](https://github.com/bsorrentino/langgraph4j/commit/aa9d0d0881aff068b88866e1eae3311ec51a4c50))

 -  **howto**  update index ([f26af20fbe63a44](https://github.com/bsorrentino/langgraph4j/commit/f26af20fbe63a446b7c23c4b78e9ec1b24e4f4d9))

 -  remove useless infos ([57b8555df55282d](https://github.com/bsorrentino/langgraph4j/commit/57b8555df55282d3c7abd867e535c5156ccec532))

 -  **RemoveByHash.java**  remove unnecessary generic parameter in javadoc ([9ca0cfd8b5cf3ae](https://github.com/bsorrentino/langgraph4j/commit/9ca0cfd8b5cf3ae035926f8d6d7128d4974ff219))

 -  **subgraph**  add subgraph documentation ([40269ef1b1f3c59](https://github.com/bsorrentino/langgraph4j/commit/40269ef1b1f3c5983e0af8068c93a62eea9ec55d))
     > - update markdown files
     > - update related notebooks
     > resolve #49

 -  update changeme ([8b72c64cf3cd415](https://github.com/bsorrentino/langgraph4j/commit/8b72c64cf3cd4156e6c4956813630fa322b1181e))

 -  **how-tos**  add subgraph images ([43b966e50bd0eda](https://github.com/bsorrentino/langgraph4j/commit/43b966e50bd0edaada19832f18fe562a16db9957))

 -  update subgraph images ([ffb6d02cfad30fa](https://github.com/bsorrentino/langgraph4j/commit/ffb6d02cfad30fa9f03e91a6b015bdd4f7be715d))

 -  update changeme ([b7be4727f4efe06](https://github.com/bsorrentino/langgraph4j/commit/b7be4727f4efe0648e368500a37d40dc36100c15))

 -  **how-tos**  add subgraph how-to ([8f6d15a57295d68](https://github.com/bsorrentino/langgraph4j/commit/8f6d15a57295d68c5c0755a389d9d078b6b34052))

 -  update changeme ([e104c17be828273](https://github.com/bsorrentino/langgraph4j/commit/e104c17be82827307de3e52921d054df0ef731a4))

 -  update changeme ([6fb4093bef1673c](https://github.com/bsorrentino/langgraph4j/commit/6fb4093bef1673c9704320483f7f08c73fda6993))

 -  update changeme ([57b8853ae12e0e8](https://github.com/bsorrentino/langgraph4j/commit/57b8853ae12e0e89bedaa102522ca269833e4853))

 -  update changeme ([fab30d98d302e46](https://github.com/bsorrentino/langgraph4j/commit/fab30d98d302e4680bb8803b6ffcd5d227416569))

 -  update changeme ([cbb952aad441d77](https://github.com/bsorrentino/langgraph4j/commit/cbb952aad441d77a6b83d9f1627486da2b253d27))

 -  **CompileConfig.java**  add javadoc ([939b0c05f12ff16](https://github.com/bsorrentino/langgraph4j/commit/939b0c05f12ff161a38fbb13098379cdbb2106dc))

 -  move to next release 1.4.0-beta1 ([736437dbddb0865](https://github.com/bsorrentino/langgraph4j/commit/736437dbddb0865e816260fb875357615bb06b59))

 -  referes to last 1.4-SNAPSHOT ([2d9948e51f71f45](https://github.com/bsorrentino/langgraph4j/commit/2d9948e51f71f45e02b9b88eae9d119b45f0aa9b))

 -  update changeme ([be5f28783addb93](https://github.com/bsorrentino/langgraph4j/commit/be5f28783addb93470db10de23a23b0ea68ac482))

 -  update changeme ([3d5ae1ec2c4477b](https://github.com/bsorrentino/langgraph4j/commit/3d5ae1ec2c4477b5d493ff3326ab636437ea6f57))


### Refactor

 -  **CompiledGraph.java**  streamline method signatures by removing unnecessary exceptions ([3d7522603f9c97c](https://github.com/bsorrentino/langgraph4j/commit/3d7522603f9c97cb160547a1ce58ab4645c6bf24))
   
 -  create package internal containing internal classes ([f2af63d2b9c1780](https://github.com/bsorrentino/langgraph4j/commit/f2af63d2b9c1780988710c0e27ca60e769979712))
    > prepare for java  module integration

 -  **CompiledGraph.java**  remove redundant graph validation and edge processing ([dd24149f79d464c](https://github.com/bsorrentino/langgraph4j/commit/dd24149f79d464c86d99f784c43a298a686b2ff3))
   
 -  create package internal containing internal classes ([d65b4a509b35c0b](https://github.com/bsorrentino/langgraph4j/commit/d65b4a509b35c0bcbb9c78ee506770ddf06a0d44))
    > prepare for java  module integration

 -  **Edge.java**  update method signature and simplify target id handling ([ce1ec881697abff](https://github.com/bsorrentino/langgraph4j/commit/ce1ec881697abff807f3307664f1b6fddfbe4ac8))
    > Updated the &#x60;withSourceAndTargetIdsUpdated&#x60; method in &#x60;Edge.java&#x60; to use a &#x60;Function&lt;String, EdgeValue&lt;State&gt;&gt;&#x60; instead of &#x60;Function&lt;String, String&gt;&#x60; for target ids.
 > Added constructors that allow creating an &#x60;EdgeValue&#x60; with only an ID or only a condition value.

 -  **StateGraph.java**  remove unnecessary parameters from EdgeValue ([ba726369987b7d1](https://github.com/bsorrentino/langgraph4j/commit/ba726369987b7d1c8221cd323120c8e79f563fef))
    > - Removed redundant parameters in &#x60;EdgeValue&#x60; constructor calls to simplify code
 > - Updated commented out conditional logic, potentially for future use

 -  **CompileConfig.java**  replace List with Set in interrupt fields ([9e1487907ca724b](https://github.com/bsorrentino/langgraph4j/commit/9e1487907ca724b9db43477662c766ea4257cedf))
   
 -  **howtos/subgraph**  add more tests and bump to new langgraph4j SNAPSHOT ([97c94499e7ffae9](https://github.com/bsorrentino/langgraph4j/commit/97c94499e7ffae98cf3b8ef2a5bc11eb32a95d5f))
    > work on #73

 -  **howtos**  bump to new langgraph4j SNAPSHOT ([941b3c20a24da40](https://github.com/bsorrentino/langgraph4j/commit/941b3c20a24da40634e1231fbd78d88af858e69a))
   
 -  create package internal containing internal classes ([5fdc50b2483b095](https://github.com/bsorrentino/langgraph4j/commit/5fdc50b2483b0952e4f6691a57e5284a96e5a678))
    > prepare for java  module integration

 -  **core/CompileConfig.java**  update interrupt fields to List ([29ccd1ed96cff0c](https://github.com/bsorrentino/langgraph4j/commit/29ccd1ed96cff0c98038d240697a727ea3ec8713))
    > - Changed &#x60;interruptBefore&#x60; and &#x60;interruptAfter&#x60; from String arrays to Lists to allow for more flexible configuration.
 > - Deprecated the old getter methods in favor of new methods that return immutable lists.
 > - Add copy constructor.

 -  **CompiledGraph.java**  use new Edge.withSourceAndTargetIdsUpdated that accept Function<String,EdgeValue> ([14b1e51e0c6730b](https://github.com/bsorrentino/langgraph4j/commit/14b1e51e0c6730b0f0ce4789ef281df962adb4ff))
   
 -  **Node.java**  refine node class hierarchy ([ab83ffc300f1d82](https://github.com/bsorrentino/langgraph4j/commit/ab83ffc300f1d8221287d02e47d68dcb185daf16))
   
 -  **CompiledGraph.java**  Renamed method to correctly filter out sub-state graph nodes ([dbe8d473e693343](https://github.com/bsorrentino/langgraph4j/commit/dbe8d473e69334325a98d65d9c6f36e4378be63a))
    > - Renamed &#x60;onlySubStateGraphNodes&#x60; to &#x60;withoutSubGraphNodes&#x60;
 > - Updated variable names and references throughout the method for clarity
 > - Changed the creation and handling of &#x60;resultEdges&#x60; to use a single &#x60;StateGraphNodesAndEdges&#x60; object

 -  **Edge.java**  update EdgeValue to handle multiple target IDs ([46dbdff97da99f4](https://github.com/bsorrentino/langgraph4j/commit/46dbdff97da99f405a34eaf588d5c693a0318059))
    > - add &#x60;EdgeCondition&#x60; and &#x60;AsyncEdgeAction&#x60; class in the same file for improve incapsulation and maintanability
 > - Updated &#x60;EdgeValue&#x60; to use a new method &#x60;withTargetIdsUpdated&#x60; which handles updates for multiple target IDs.
 > - Removed the  &#x60;EdgeCondition&#x60; and &#x60;EdgeValue&#x60; classes as independent file unit
 > work on #73

 -  **DiagramGenerator.java**  simplify subgraph detection ([08917371818ff51](https://github.com/bsorrentino/langgraph4j/commit/08917371818ff51abab99e471350add977bb8dd0))
   
 -  **StateGraph.java**  update node construction and refactor subgraph management ([2070c009f43a6e3](https://github.com/bsorrentino/langgraph4j/commit/2070c009f43a6e3e4fea4126233f54ee7022bda4))
    > - Replace &#x60;SubGraphNodeAction&#x60; with &#x60;SubCompiledGraphNode&#x60;
 > - Rename methods to clarify their purpose (&#x60;onlySubStateGraphNodes&#x60;, &#x60;exceptSubStateGraphNodes&#x60;)

 -  **CollectionsUtils.java**  update methods to improve null safety and deprecate old factory methods ([369992230c614e5](https://github.com/bsorrentino/langgraph4j/commit/369992230c614e51b42d2592f64714d9d919d760))
   
 -  **langchain4jToolNodeTest.java**  remove deprecation in update tool parameters definition ([f0436296f22163a](https://github.com/bsorrentino/langgraph4j/commit/f0436296f22163a3a9bb406d21f73582c7719c43))
   
 -  **Node.java**  introduce interfaces and classes for subgraph handling ([a673f8ef0dd3465](https://github.com/bsorrentino/langgraph4j/commit/a673f8ef0dd34656a2e383e9258542de7c1d6ca4))
    > Refactored SubGraphNode to interface, added concrete implementations for different types of subgraph nodes (State, Compiled).

 -  **Edge.java**  replace Collection with StateGraph.Nodes for improved performance and readability ([4f2f10d53697ed7](https://github.com/bsorrentino/langgraph4j/commit/4f2f10d53697ed7e451a5aa5b7739062f417fd91))
    > - Updated &#x60;validate&#x60; methods to use &#x60;StateGraph.Nodes.anyMatchById&#x60; instead of manual containment checks within a collection, enhancing both performance and code clarity.
 > work on #73

 -  **StateGraph**  consolidate subgraph processing into a single method ([d2c4fd5ebd1cdc2](https://github.com/bsorrentino/langgraph4j/commit/d2c4fd5ebd1cdc20f9ed8db6a84b4ee3038acfd6))
    > - Extracted subgraph processing logic into &#x60;StateGraph::processSubgraphs&#x60;
 > - Moved subgraph-related updates to new &#x60;StateGraph::Nodes&#x60; and &#x60;StateGraph::Edges&#x60; classes for better separation of concerns
 > work on #73

 -  **DiagramGenerator.java**  access node elements directly ([770c0036cc2c04e](https://github.com/bsorrentino/langgraph4j/commit/770c0036cc2c04e0f93dff18cef7af0b1476cc2a))
    > work on #73

 -  **StateGraph.java**  update node handling with new Nodes class ([226523887b4478a](https://github.com/bsorrentino/langgraph4j/commit/226523887b4478af5b69b91e6297d9bcd6277c2d))
    > - Introduced a new &#x60;Nodes&#x60; class to encapsulate management of graph nodes, providing methods for checking if a node with a given ID exists, finding sub-graph nodes, and filtering out sub-graph nodes.
 > - Updated the &#x60;StateGraph&#x60; class to use the new &#x60;Nodes&#x60; class for managing nodes, improving code readability and modularity.
 > - Modified edge lookup methods from &#x60;findEdgeBySourceId&#x60; and &#x60;findEdgesByTargetId&#x60; to &#x60;edgeBySourceId&#x60; and &#x60;edgesByTargetId&#x60; .
 > work on #73

 -  **DiagramGenerator.java**  update edge processing streams to use 'elements' ([68ae2df39d0c42a](https://github.com/bsorrentino/langgraph4j/commit/68ae2df39d0c42a0f3c8f5962df970f7cc5ac2d9))
    > work on #73

 -  **StateGraph.java**  update edge collection management and improve graph validation ([de25bb2ff09884b](https://github.com/bsorrentino/langgraph4j/commit/de25bb2ff09884b1301f10173097669db1e4dc17))
    > Refactored &#x60;StateGraph.java&#x60; to use a custom &#x60;Edges&lt;State&gt;&#x60; class for managing edge collections, enhancing maintainability.
 > Updated methods to find edges by source and target IDs, and added a comprehensive &#x60;validateGraph()&#x60; method to ensure edge consistency during compilation.
 > work on #73

 -  **EdgeValue.java**  update EdgeValue implementation to use record and add withTargetIdUpdated method ([7cba7b784c4d0f1](https://github.com/bsorrentino/langgraph4j/commit/7cba7b784c4d0f1b21e56f0db256a452f92310f4))
    > - Adding a new method, &#x60;withTargetIdUpdated&#x60;, which updates the &#x60;id&#x60; field while copying other values or modifying mappings based on the input function.
 > work on #73


### ALM 

 -  move to next SNAPSHOT ([3ee93e64565e9b4](https://github.com/bsorrentino/langgraph4j/commit/3ee93e64565e9b45c3f245ddc917d3b50724c1ae))
   
 -  move to next release 1.4.0-beta1 ([f8e200092f5e1a4](https://github.com/bsorrentino/langgraph4j/commit/f8e200092f5e1a4ff5a6eaabfd7a1d2c2ae118d1))
   
 -  bump to next SNAPSHOT ([c22c68c7412c347](https://github.com/bsorrentino/langgraph4j/commit/c22c68c7412c3473d122e014e467d4f57f5b11d5))
   

### Test 

 -  **SubGraphTest.java**  Add tests for subgraph interruption ([f69afebc18ae52b](https://github.com/bsorrentino/langgraph4j/commit/f69afebc18ae52ba3b84eafbfc3e166c7347dfa9))
    > work on #73

 -  **subgraph**  add more subgraph tests ([80dff74c93fa5d1](https://github.com/bsorrentino/langgraph4j/commit/80dff74c93fa5d1b2a9fca05d326795b38f74319))
    > work on #73

 -  add unit tests for subgraph interruptions ([5a1363b11c3c787](https://github.com/bsorrentino/langgraph4j/commit/5a1363b11c3c78704317002ab261c5598701eb3b))
    > work on #73

 -  add new test cases for subgraph merge ([38a21cc0381c288](https://github.com/bsorrentino/langgraph4j/commit/38a21cc0381c2886819af359a46252265d813ba3))
    > - Added new test cases to cover different scenarios in merging subgraphs.
 > work on #73

 -  **mergedgraph**  test of merging edges and nodes logic into parent graph ([69cf1c3c068c40c](https://github.com/bsorrentino/langgraph4j/commit/69cf1c3c068c40c6d0e765454f560b5b46cd72cd))
    > work on #73






<!-- "name: v1.4.0-beta1" is a release tag -->

## [v1.4.0-beta1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.4.0-beta1) (2025-02-11)

### Features

 *  **representation**  set dotted line for conditional edges ([36fe11fffec3968](https://github.com/bsorrentino/langgraph4j/commit/36fe11fffec39686883e0c65b062ecf4e94380a0))
   
 *  **subgraph**  allow produce graph containing  subgraph representations both original (from state graph ) and merged  (from compile graph) ([b1550c773d3cf21](https://github.com/bsorrentino/langgraph4j/commit/b1550c773d3cf21424ba7cca24fad7e360e54a81))
   
 *  **core/CompiledGraph.java**  enhance subgraph processing in state graph ([94322b6ef726f7e](https://github.com/bsorrentino/langgraph4j/commit/94322b6ef726f7e9a46bc82a6036cd97480173b5))
     > It ensures that interruptions (nodes marked as &quot;before&quot;) are correctly redirected to the real target ID after subgraph expansion.
     > work on #73
   
 *  **core/CompiledGraph.java**  enhance subgraph processing in state graph ([00679e902d3f9ab](https://github.com/bsorrentino/langgraph4j/commit/00679e902d3f9ab8f42427941f75a1d54d289b02))
     > It ensures that interruptions (nodes marked as &quot;before&quot;) are correctly redirected to the real target ID after subgraph expansion.
     > work on #73
   
 *  **core/CompiledGraph.java**  add interruption node checks ([bf97c1b8e51a742](https://github.com/bsorrentino/langgraph4j/commit/bf97c1b8e51a74225bd956abd40c7338c506f065))
     > Added checks for interruption nodes before processing state graph nodes and edges.
   
 *  **core/StateGraph.java**  Add error message for non-existent interruption node ([d8decf4cde589e7](https://github.com/bsorrentino/langgraph4j/commit/d8decf4cde589e7d0c084c8da22fc6cec4ba7e4f))
     > Added an error message to handle the scenario where a node configured as an interruption does not exist in the StateGraph.
   
 *  **core//SubGraphNode.java**  introduce SubGraphNode interface ([4065ce68c20333f](https://github.com/bsorrentino/langgraph4j/commit/4065ce68c20333f3f7d863fb6d467db748dca1f1))
     > Add a new interface &#x60;SubGraphNode&#x60; in package &#x60;org.bsc.langgraph4j&#x60;, which defines methods related to managing sub-graph nodes with specific states.
     > This interface includes a method for obtaining the sub-graph and a static utility method for formatting node IDs to distinct subgraph nodes from standard ones
     > work on #73
   
 *  **core/CompiledGraph.java**  Refactor `CompiledGraph` to prepare for support of sub-graphs merge ([6e534cc81cf880b](https://github.com/bsorrentino/langgraph4j/commit/6e534cc81cf880b2db5865ef50d53dbbe1332cb0))
     > - Removed deprecated methods &#x60;getEntryPoint&#x60; and &#x60;getFinishPoint&#x60;
     > work on #73
   
 *  **core/Edge.java**  add methods for target ID matching and source/target id updates ([4971ad41235a7f7](https://github.com/bsorrentino/langgraph4j/commit/4971ad41235a7f728130d31908c483c7a08fab72))
     > - Added &#x60;anyMatchByTargetId&#x60; method to check if there is a target with a specific ID by comparing IDs or value mappings.
     > - Created &#x60;withSourceAndTargetIdsUpdated&#x60; method to update both the source and target IDs in an Edge, leveraging a Node and provided functions for new IDs.
     > work on #73
   

### Bug Fixes

 -  **site**  rename folder studio/core to studio/base ([17610eeb37a20e3](https://github.com/bsorrentino/langgraph4j/commit/17610eeb37a20e34b395065b18f681f5a77658da))

 -  **core/Node.java**  Ensured the `equals` method only casts to `Node<?>` if the object is not null, ([79f83954ba0308c](https://github.com/bsorrentino/langgraph4j/commit/79f83954ba0308c2abe987c9c9f84714bf714525))
     > work on #73


### Documentation

 -  move to next release 1.4.0-beta1 ([55863cd2ac9ead2](https://github.com/bsorrentino/langgraph4j/commit/55863cd2ac9ead27d98fe647cfcb6c717ec9ff33))

 -  **core/CompileConfig.java**  add javadoc ([bc107c8426e4aa3](https://github.com/bsorrentino/langgraph4j/commit/bc107c8426e4aa3496171ea59043e56ac942f000))

 -  referes to last 1.4-SNAPSHOT ([36cbf7212e128fa](https://github.com/bsorrentino/langgraph4j/commit/36cbf7212e128facb9837903e5cd3d37c9ead9fd))

 -  **sudio**  update dependencies to use correct artifact IDs ([8bb9f2592ce0d0b](https://github.com/bsorrentino/langgraph4j/commit/8bb9f2592ce0d0b3948b40377684d6c1bbd1b983))

 -  update release documentation ([4e56cd883ba28fe](https://github.com/bsorrentino/langgraph4j/commit/4e56cd883ba28fe63cbf5cf49c6f43419ce03cda))

 -  update changeme ([8c7d844563a94a8](https://github.com/bsorrentino/langgraph4j/commit/8c7d844563a94a8664c0d6557a37cd572aaebd92))


### Refactor

 -  create package internal containing internal classes ([76de12bfdd0cd83](https://github.com/bsorrentino/langgraph4j/commit/76de12bfdd0cd830b70c364414f0046d87e5fc17))
    > prepare for java  module integration

 -  create package internal containing internal classes ([04594fdbd28a184](https://github.com/bsorrentino/langgraph4j/commit/04594fdbd28a1843ed7a79bced618c88d66b1c09))
    > prepare for java  module integration

 -  create package internal containing internal classes ([f50f85ed348559d](https://github.com/bsorrentino/langgraph4j/commit/f50f85ed348559dafcb8015f2acafaeeb9d871b2))
    > prepare for java  module integration

 -  **core/CompileConfig.java**  replace List with Set in interrupt fields ([3a646b34dafc912](https://github.com/bsorrentino/langgraph4j/commit/3a646b34dafc91227ef7f129238bbe9830f0acec))
   
 -  **core/CompiledGraph.java**  use new Edge.withSourceAndTargetIdsUpdated that accept Function<String,EdgeValue> ([07dfb3bbf4addea](https://github.com/bsorrentino/langgraph4j/commit/07dfb3bbf4addea3e41cf73272fc3c5d4ac4f981))
   
 -  **StateGraph.java**  remove unnecessary parameters from EdgeValue ([91e7798fe922224](https://github.com/bsorrentino/langgraph4j/commit/91e7798fe92222433b62b801a194031f2abb465f))
    > - Removed redundant parameters in &#x60;EdgeValue&#x60; constructor calls to simplify code
 > - Updated commented out conditional logic, potentially for future use

 -  **core/Edge.java**  update method signature and simplify target id handling ([9ddf1d0710f1bb9](https://github.com/bsorrentino/langgraph4j/commit/9ddf1d0710f1bb9163c6d36a41b4bbe22584d830))
    > Updated the &#x60;withSourceAndTargetIdsUpdated&#x60; method in &#x60;Edge.java&#x60; to use a &#x60;Function&lt;String, EdgeValue&lt;State&gt;&gt;&#x60; instead of &#x60;Function&lt;String, String&gt;&#x60; for target ids.
 > Added constructors that allow creating an &#x60;EdgeValue&#x60; with only an ID or only a condition value.

 -  **core/CompileConfig.java**  update interrupt fields to List ([4729fd8cceabdcb](https://github.com/bsorrentino/langgraph4j/commit/4729fd8cceabdcb44b48f1454816b0365afbf96b))
    > - Changed &#x60;interruptBefore&#x60; and &#x60;interruptAfter&#x60; from String arrays to Lists to allow for more flexible configuration.
 > - Deprecated the old getter methods in favor of new methods that return immutable lists.
 > - Add copy constructor.

 -  **howtos/subgraph**  add more tests and bump to new langgraph4j SNAPSHOT ([d8cd2a2ca03f147](https://github.com/bsorrentino/langgraph4j/commit/d8cd2a2ca03f1470edb4ca0d606a638d9c8c9eb3))
    > work on #73

 -  **howtos**  bump to new langgraph4j SNAPSHOT ([f43122ed4464445](https://github.com/bsorrentino/langgraph4j/commit/f43122ed4464445aa3de5981cc1ae0c9997abe72))
   
 -  **core/CompiledGraph.java**  remove redundant graph validation and edge processing ([5ea5d6b1c4a06a6](https://github.com/bsorrentino/langgraph4j/commit/5ea5d6b1c4a06a64af9310019f33e8fe2933cbe2))
   
 -  **core/Node.java**  refine node class hierarchy ([3752498e84201cd](https://github.com/bsorrentino/langgraph4j/commit/3752498e84201cdd25518d28128158f3940d2054))
   
 -  **core/Edge.java**  update EdgeValue to handle multiple target IDs ([a6d269e88df9320](https://github.com/bsorrentino/langgraph4j/commit/a6d269e88df93209bf8c544a7bb702a57fcbd594))
    > - add &#x60;EdgeCondition&#x60; and &#x60;AsyncEdgeAction&#x60; class in the same file for improve incapsulation and maintanability
 > - Updated &#x60;EdgeValue&#x60; to use a new method &#x60;withTargetIdsUpdated&#x60; which handles updates for multiple target IDs.
 > - Removed the  &#x60;EdgeCondition&#x60; and &#x60;EdgeValue&#x60; classes as independent file unit
 > work on #73

 -  **CompiledGraph.java**  Renamed method to correctly filter out sub-state graph nodes ([bb78ac3ecb40bee](https://github.com/bsorrentino/langgraph4j/commit/bb78ac3ecb40beec027b25f3067228dea6f3e13f))
    > - Renamed &#x60;onlySubStateGraphNodes&#x60; to &#x60;withoutSubGraphNodes&#x60;
 > - Updated variable names and references throughout the method for clarity
 > - Changed the creation and handling of &#x60;resultEdges&#x60; to use a single &#x60;StateGraphNodesAndEdges&#x60; object

 -  **core/DiagramGenerator.java**  simplify subgraph detection ([c7aa3919093d4df](https://github.com/bsorrentino/langgraph4j/commit/c7aa3919093d4df56b6fb508a3c0fcdd3a3620b5))
   
 -  **core/StateGraph.java**  update node construction and refactor subgraph management ([6dc6be4fe81a9d4](https://github.com/bsorrentino/langgraph4j/commit/6dc6be4fe81a9d4b54f4f4f822d66212b4c41e85))
    > - Replace &#x60;SubGraphNodeAction&#x60; with &#x60;SubCompiledGraphNode&#x60;
 > - Rename methods to clarify their purpose (&#x60;onlySubStateGraphNodes&#x60;, &#x60;exceptSubStateGraphNodes&#x60;)

 -  **core/Node.java**  introduce interfaces and classes for subgraph handling ([f98c40ee56e30e1](https://github.com/bsorrentino/langgraph4j/commit/f98c40ee56e30e1f438a8291d03342b8ba088b5f))
    > Refactored SubGraphNode to interface, added concrete implementations for different types of subgraph nodes (State, Compiled).

 -  **langchain4jToolNodeTest.java**  remove deprecation in update tool parameters definition ([c4b90d64f961bbf](https://github.com/bsorrentino/langgraph4j/commit/c4b90d64f961bbfc4726a7a9835c56e52618d835))
   
 -  **Edge.java**  replace Collection with StateGraph.Nodes for improved performance and readability ([9e703115afe22bf](https://github.com/bsorrentino/langgraph4j/commit/9e703115afe22bf2113eb20ba9e03420484c8b1d))
    > - Updated &#x60;validate&#x60; methods to use &#x60;StateGraph.Nodes.anyMatchById&#x60; instead of manual containment checks within a collection, enhancing both performance and code clarity.
 > work on #73

 -  **core/StateGraph**  consolidate subgraph processing into a single method ([03abb16e604c886](https://github.com/bsorrentino/langgraph4j/commit/03abb16e604c88633f66524cfcd8fc9e9dac2aa3))
    > - Extracted subgraph processing logic into &#x60;StateGraph::processSubgraphs&#x60;
 > - Moved subgraph-related updates to new &#x60;StateGraph::Nodes&#x60; and &#x60;StateGraph::Edges&#x60; classes for better separation of concerns
 > work on #73

 -  **core/DiagramGenerator.java**  access node elements directly ([6792e9fdb586042](https://github.com/bsorrentino/langgraph4j/commit/6792e9fdb586042c145b7b6755728670f3adb25a))
    > work on #73

 -  **core/StateGraph.java**  update node handling with new Nodes class ([3e45264449ddebf](https://github.com/bsorrentino/langgraph4j/commit/3e45264449ddebfd6278754582bc3fdd0766e4b5))
    > - Introduced a new &#x60;Nodes&#x60; class to encapsulate management of graph nodes, providing methods for checking if a node with a given ID exists, finding sub-graph nodes, and filtering out sub-graph nodes.
 > - Updated the &#x60;StateGraph&#x60; class to use the new &#x60;Nodes&#x60; class for managing nodes, improving code readability and modularity.
 > - Modified edge lookup methods from &#x60;findEdgeBySourceId&#x60; and &#x60;findEdgesByTargetId&#x60; to &#x60;edgeBySourceId&#x60; and &#x60;edgesByTargetId&#x60; .
 > work on #73

 -  **core/DiagramGenerator.java**  update edge processing streams to use 'elements' ([e59edfd4f6e62ec](https://github.com/bsorrentino/langgraph4j/commit/e59edfd4f6e62ec3ba2e4f681c3698385cd8c11a))
    > work on #73

 -  **core/StateGraph.java**  update edge collection management and improve graph validation ([898f5fd2d435492](https://github.com/bsorrentino/langgraph4j/commit/898f5fd2d4354923054cd36a02ccb327422ab766))
    > Refactored &#x60;StateGraph.java&#x60; to use a custom &#x60;Edges&lt;State&gt;&#x60; class for managing edge collections, enhancing maintainability.
 > Updated methods to find edges by source and target IDs, and added a comprehensive &#x60;validateGraph()&#x60; method to ensure edge consistency during compilation.
 > work on #73

 -  **core/EdgeValue.java**  update EdgeValue implementation to use record and add withTargetIdUpdated method ([9891961af89cd3f](https://github.com/bsorrentino/langgraph4j/commit/9891961af89cd3f5b7eac12775785d8c26985b56))
    > - Adding a new method, &#x60;withTargetIdUpdated&#x60;, which updates the &#x60;id&#x60; field while copying other values or modifying mappings based on the input function.
 > work on #73

 -  **core/Node.java**  introduce class hierarchy for better structure ([f86c51906faf960](https://github.com/bsorrentino/langgraph4j/commit/f86c51906faf960842e3104eee9ccd2e0bd9e4db))
    > - Change &#x60;Node&#x60; from a record to a class for more flexibility.
 > - Add getter methods for &#x60;id&#x60; and &#x60;actionFactory&#x60;.
 > - Introduce &#x60;ParallelNode&#x60; and &#x60;SubGraphNode&#x60; subclasses to handle complex node structures.
 > - Update constructors and refactor method implementations accordingly.
 > - Add Javadoc comments for better understanding of each type, method, and field.
 > work on #73


### ALM 

 -  move to next release 1.4.0-beta1 ([083453711d86386](https://github.com/bsorrentino/langgraph4j/commit/083453711d8638658d8223995fac45dc6e6af3a0))
   
 -  bump to next SNAPSHOT ([a4bbe3dbbaf71fd](https://github.com/bsorrentino/langgraph4j/commit/a4bbe3dbbaf71fd63fc17b3640a553a674614007))
   
 -  bump to next SNAPSHOT ([bb7e66dcf22d0b4](https://github.com/bsorrentino/langgraph4j/commit/bb7e66dcf22d0b4fa59c9866468b973288cbd4ef))
   

### Test 

 -  **core/SubGraphTest.java**  Add tests for subgraph interruption ([a22d70d974f1064](https://github.com/bsorrentino/langgraph4j/commit/a22d70d974f1064a72c0ad5f642290ce9d2828d9))
    > work on #73

 -  add new test cases for subgraph merge ([b267d71d1d02b38](https://github.com/bsorrentino/langgraph4j/commit/b267d71d1d02b38bc83400b9b0c024d203573606))
    > - Added new test cases to cover different scenarios in merging subgraphs.
 > work on #73

 -  add unit tests for subgraph interruptions ([3c0937713fe38c2](https://github.com/bsorrentino/langgraph4j/commit/3c0937713fe38c2e018c542687f7a3557a628f2d))
    > work on #73

 -  **subgraph**  add more subgraph tests ([70c2ac6963a2088](https://github.com/bsorrentino/langgraph4j/commit/70c2ac6963a2088ed714257b990d4658771c05fd))
    > work on #73

 -  **mergedgraph**  test of merging edges and nodes logic into parent graph ([103becc148243b2](https://github.com/bsorrentino/langgraph4j/commit/103becc148243b23e7b6a2fee5d74f9580f74579))
    > work on #73






<!-- "name: v1.3.1" is a release tag -->

## [v1.3.1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.3.1) (2025-02-04)

### Features

 *  **studio**  add spring boot reference implementation ([c72c00be62a5459](https://github.com/bsorrentino/langgraph4j/commit/c72c00be62a54592daee02c317e4d0e049594324))
   
 *  **studio**  deploy webui to spring boot implementation ([fdea8c674098c32](https://github.com/bsorrentino/langgraph4j/commit/fdea8c674098c32af0bded71ad88f2d26d0d403c))
   
 *  **studio**  setup new module for run studio from springboot ([1df22b7aa8dc43e](https://github.com/bsorrentino/langgraph4j/commit/1df22b7aa8dc43e459a899b2905eeac809204f35))
   


### Documentation

 -  bump to new version 1.3.1 ([1c5e2c0d25eb556](https://github.com/bsorrentino/langgraph4j/commit/1c5e2c0d25eb556d08984d55e144a3c05a5fab18))

 -  **how-to**  update notebook documentation ([f7e1fe67fc83bf2](https://github.com/bsorrentino/langgraph4j/commit/f7e1fe67fc83bf28e995d5efccf785928e99770b))
     > work on #78

 -  **studio**  update jetty  and add spring boot documentation ([ae3df659f933062](https://github.com/bsorrentino/langgraph4j/commit/ae3df659f933062dda6d5e6ed130ac66494b5f84))

 -  **site**  remove name from bannerLeft ([e54b2807cd6b1ea](https://github.com/bsorrentino/langgraph4j/commit/e54b2807cd6b1eafa862fba218962f8bbfa41e49))

 -  **site**  Update XML structure to use Maven Site schema ([05ef8e6d5631d73](https://github.com/bsorrentino/langgraph4j/commit/05ef8e6d5631d734b7a6b55071968969ea39f3d3))
     > - Switched from &#x60;site&#x60; to &#x60;project&#x60; namespace for improved flexibility

 -  **hot-tos**  add parallel-branch images ([fc594ed27e4f1fc](https://github.com/bsorrentino/langgraph4j/commit/fc594ed27e4f1fc73ebd4e8d464a78690c407e70))

 -  update project documentation and feature status ([3ab3454f0adc1bd](https://github.com/bsorrentino/langgraph4j/commit/3ab3454f0adc1bdcb2cdee216bd96676a9348a86))

 -  update changeme ([8134fb24858b1a4](https://github.com/bsorrentino/langgraph4j/commit/8134fb24858b1a4d86ceb1e3ad35736fc1020469))


### Refactor

 -  **AbstractLangGraphStudioConfig.java**  remove final method qualifier ([333bf29418aeae0](https://github.com/bsorrentino/langgraph4j/commit/333bf29418aeae0d113b11f977567c4d0876e544))
   
 -  **studio**  change modules layout for studio implementation ([fbad211339b1440](https://github.com/bsorrentino/langgraph4j/commit/fbad211339b1440df0bb20891c1c0ee684cd188f))
    > - Make it more flexible to allow different studio server implementation


### ALM 

 -  bump to new version 1.3.1 ([903a9a1dc9b5d09](https://github.com/bsorrentino/langgraph4j/commit/903a9a1dc9b5d09619af005ae82e73873802c829))
   
 -  bump version to SNAPSHOT ([39dfc39a7213689](https://github.com/bsorrentino/langgraph4j/commit/39dfc39a72136892a9e57c511d25bb7e25414628))
   

### Test 

 -  **how-to**  create notebook to verify issue #78 ([3a3527430d6fabc](https://github.com/bsorrentino/langgraph4j/commit/3a3527430d6fabc8da31c45624c50e817e7396ea))
    > work on #78






<!-- "name: v1.3.0" is a release tag -->

## [v1.3.0](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.3.0) (2025-01-30)

### Features

 *  **how-to/parallel-branch**  add more usage examples ([1fcb7cb77624da6](https://github.com/bsorrentino/langgraph4j/commit/1fcb7cb77624da6ccfad97654d392b68c93804a5))
   
 *  **prebuilt/MessagesState.java**  add utility method 'lastMinus(int)' for accessing messages state ([e0610a2d6773e0e](https://github.com/bsorrentino/langgraph4j/commit/e0610a2d6773e0e764398796b9739f99803baaa0))
   
 *  **how-to**  add notebook for parallel branch execution ([46891fc5048897a](https://github.com/bsorrentino/langgraph4j/commit/46891fc5048897a61af9d1fbd60d30fd1dfdd6bb))
   
 *  **prebuilt**  Added MessagesState and MessagesStateGraph classes ([7859c321f04de55](https://github.com/bsorrentino/langgraph4j/commit/7859c321f04de556e458125676ecbd5a84e57943))
     > This commit introduces new classes &#x60;MessagesState&#x60; and &#x60;MessagesStateGraph&#x60; in the &#x60;org.bsc.langgraph4j.prebuilt&#x60; package as utilities classes
     > - **MessagesState&lt;T&gt;**: manages a collection of messages .
     > - **MessagesStateGraph&lt;T&gt;** a  &#x60;StateGraph&#x60; specialized for use of  &#x60;MessagesState&#x60;.
   
 *  **CompiledGraph**  Refactor compiled graph edge processing ([c8ae36a87568bc0](https://github.com/bsorrentino/langgraph4j/commit/c8ae36a87568bc09c7f665c6aa57fe18bba41858))
     > - Update edge mapping logic to handle parallel nodes and conditional edges
     > - Introduce parallel action nodes for handling multiple targets
     > - Remove deprecated methods getEntryPoint() and getFinishPoint()
     > work on #72
   
 *  **Node**  add parallel execution support ([d50f56c5937251f](https://github.com/bsorrentino/langgraph4j/commit/d50f56c5937251f77b931b8d0f77fb01db69a310))
     > work on #72
   


### Documentation

 -  bump release version to 1.3.0 ([5b49118781c29e2](https://github.com/bsorrentino/langgraph4j/commit/5b49118781c29e2f5ba6bc8daffb484562eb5048))

 -  **how-tos**  update site documentation ([c9ffa56575a1c8b](https://github.com/bsorrentino/langgraph4j/commit/c9ffa56575a1c8b8b929ee271a61a5c23ccaa624))

 -  **how-tos/adaptiverag**  update documentation ([c3988d4cc635506](https://github.com/bsorrentino/langgraph4j/commit/c3988d4cc635506c62bcc331d7c311a19ae06f3a))

 -  **prebuilt/MessageStateGraph**  add javadoc ([f8c1670adf64e7d](https://github.com/bsorrentino/langgraph4j/commit/f8c1670adf64e7d54be3e0d4cfaef04ba1c8b66e))

 -  **how-tos**  update documentation ([0c06feb989fb1cd](https://github.com/bsorrentino/langgraph4j/commit/0c06feb989fb1cd5170a3d328f69b69ffb3e0c55))

 -  **how-tos**  update documentation ([9ba90234185787a](https://github.com/bsorrentino/langgraph4j/commit/9ba90234185787a5853aad7de998756336997816))

 -  **how-tos**  update documentation ([5497dfc2f0e96da](https://github.com/bsorrentino/langgraph4j/commit/5497dfc2f0e96daaada93be767ebc38c0f167d23))

 -  **how-tos**  update documentation ([cba67cbc4139a95](https://github.com/bsorrentino/langgraph4j/commit/cba67cbc4139a9507fe3c50372d0226a120c2cce))

 -  update javadoc ([3c392ea51d5c46d](https://github.com/bsorrentino/langgraph4j/commit/3c392ea51d5c46d02086ac24f4fc5a3dfb9f49d5))

 -  update javadoc ([80e592dbe6935d7](https://github.com/bsorrentino/langgraph4j/commit/80e592dbe6935d74bf718060b2cbb3c62e6442f4))

 -  **core**  update documentation ([c3284437ea7edff](https://github.com/bsorrentino/langgraph4j/commit/c3284437ea7edffc6a9ca6426daf17de4974df28))

 -  **core**  update documentation ([b6589aed4da9071](https://github.com/bsorrentino/langgraph4j/commit/b6589aed4da90716ff4e6e1db1a399e05ba52c28))

 -  update changeme ([5986d795d9201de](https://github.com/bsorrentino/langgraph4j/commit/5986d795d9201de2184bf006fcc77fee9a50ef95))


### Refactor

 -  **CompiledGraph.java**  replace node removal with retrieval ([72169ebf7b4dfa0](https://github.com/bsorrentino/langgraph4j/commit/72169ebf7b4dfa01909b5eb089466fc8c03ed1e1))
    > Modified &#x60;parallelNodeStream&#x60; to retrieve nodes instead of removing.

 -  **how-to**  update langgraph version ([76680b6f086c150](https://github.com/bsorrentino/langgraph4j/commit/76680b6f086c15010cd8698ee3cd88d00055a391))
   
 -  **prebuilt/MessagesStateGraph.java**  simplify state serializer ([9581a8d918f93f6](https://github.com/bsorrentino/langgraph4j/commit/9581a8d918f93f6f4a3888679b4f5ac2e926f3d6))
    > Removed unnecessary &#x60;Serializer&#x60;

 -  **prebuilt/MessagesState.java**  make SCHEMA field public static final ([91f28290150873e](https://github.com/bsorrentino/langgraph4j/commit/91f28290150873e0e55e9bd222a2537f1c9367db))
   
 -  **prebuilt/MessagesState.java**  make message-related methods public ([6152b6820e5f7ba](https://github.com/bsorrentino/langgraph4j/commit/6152b6820e5f7ba3d63940e9ef54613b248570e9))
   
 -  **how-to**  update notebook for parallel branch execution ([cfedf4869727b0a](https://github.com/bsorrentino/langgraph4j/commit/cfedf4869727b0aa758dc51663920b70cc2ef6cb))
   
 -  **AppendableValue.java**  **AppendableValueRW.java**  mark as deprecated for removal ([85135828b6aa4bc](https://github.com/bsorrentino/langgraph4j/commit/85135828b6aa4bc272d516bef36b6dbecc517755))
   
 -  **graph**  update node validation and refactoring ([ed475c9f090082f](https://github.com/bsorrentino/langgraph4j/commit/ed475c9f090082f057118cfe669d16a383a99a53))
    > - Removed deprecated &#x60;nodeById&#x60; method and replaced it with a streamlined approach utilizing Node object directly in edge validation.
 > - Updated edge validation logic to simplify the checks for source and target nodes.
 > - Ensured consistency across validations improving error handling.
 > work on #72

 -  **Edge**  refactor edge representation to support multiple targets ([89ac2d39b0d54e8](https://github.com/bsorrentino/langgraph4j/commit/89ac2d39b0d54e8c426c6e9aec9d575cccbbcdcb))
    > work on #72

 -  **Edge.java**  change class type to record ([49a7adb44ad0e8d](https://github.com/bsorrentino/langgraph4j/commit/49a7adb44ad0e8d234a455d900d6765becc5d395))
    > Refactored &#x60;Edge&#x60; class from a Lombok-generated value object to a record for improved immutability and syntax simplicity.


### ALM 

 -  bump to new version 1.3.0 ([0a60b72d6d482be](https://github.com/bsorrentino/langgraph4j/commit/0a60b72d6d482bed275fd007b46b51e60f02d333))
   
 -  bump version to SNAPSHOT ([4e39b436511306c](https://github.com/bsorrentino/langgraph4j/commit/4e39b436511306c42c0ab2c1b4e7b8b072bd18b8))
   
 -  bump version to SNAPSHOT ([ac13077033d2be0](https://github.com/bsorrentino/langgraph4j/commit/ac13077033d2be017c18be60bae637090337a2c9))
   

### Test 

 -  **how-to**  refactor notebooks to use MessagesState utility ([e08804bede6ec27](https://github.com/bsorrentino/langgraph4j/commit/e08804bede6ec27a2cddc169810aa6a916d3741a))
   
 -  **core**  refactor to use MessagesState utility ([6c68b52491679a3](https://github.com/bsorrentino/langgraph4j/commit/6c68b52491679a334f670094f13cf77fd27193fe))
   
 -  add unit test concerning parallel branch ([60b7194d4d76cce](https://github.com/bsorrentino/langgraph4j/commit/60b7194d4d76cce360ed2f2fcbbd0f8cb9e5659e))
    > resolve #72






<!-- "name: v1.2.5" is a release tag -->

## [v1.2.5](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.2.5) (2025-01-23)

### Features

 *  **AppenderChannel**  introduce functional interface and refactoring for list updates ([d95f28191e8f8f9](https://github.com/bsorrentino/langgraph4j/commit/d95f28191e8f8f9112500002fb82ec132f7eee7c))
     > - Introduced &#x60;RemoveByHash&#x60; class.
     > - Refactored &#x60;AppenderChannel.java&#x60; to support list updates with a functional interface &#x60;RemoveIdentifier&#x60;.
     > - Consolidated the logic for evaluating list updates within the &#x60;evaluateRemoval&#x60; method.
     > - Updated test cases to cover the new functionality.
     > resolve #75
   


### Documentation

 -  bump release to 1.2.5 ([2d2b807751423da](https://github.com/bsorrentino/langgraph4j/commit/2d2b807751423dae2dbdc0fe5a54d5ccf94368d7))

 -  update javadoc ([666c25331bfeced](https://github.com/bsorrentino/langgraph4j/commit/666c25331bfeced290ca20529c32d53ed65c8c5e))

 -  update documentation ([807fcab25ea0664](https://github.com/bsorrentino/langgraph4j/commit/807fcab25ea06640e16b61b2fb78e4c888d1ec4f))
     > work on #75

 -  update changeme ([385048598b72907](https://github.com/bsorrentino/langgraph4j/commit/385048598b72907d757568c0fc9af1dfbabdb131))


### Refactor

 -  **pom.xml**  consolidate profiles ([c356d9275f85339](https://github.com/bsorrentino/langgraph4j/commit/c356d9275f85339f6ed600e697f61a091d5cbae7))
    > - Removed unused jdk-8 and jdk-17 profiles to streamline configuration and reduce complexity.


### ALM 

 -  bump to version 1.2.5 ([0d9ddfad51bec46](https://github.com/bsorrentino/langgraph4j/commit/0d9ddfad51bec464dd37f36e9d3f1ce9ce6837fb))
   
 -  bump version to SNAPSHOT ([c1044fd7013ca68](https://github.com/bsorrentino/langgraph4j/commit/c1044fd7013ca68befc28e7a6af5d58e08ce12f9))
   






<!-- "name: v1.2.4" is a release tag -->

## [v1.2.4](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.2.4) (2025-01-22)

### Features

 *  add index_dev.html for dev purpose ([3a70023b3044cff](https://github.com/bsorrentino/langgraph4j/commit/3a70023b3044cffcb7fbe856247e0b2fcd06021c))
     > work on #65
   

### Bug Fixes

 -  **studio**  reset custom url in index.html ([3d173edfea531bd](https://github.com/bsorrentino/langgraph4j/commit/3d173edfea531bd24d0e3634420444352ee5be2b))
     > resolve #65

 -  **studio**  reset custom url in index.html ([4ec85e7623b69c2](https://github.com/bsorrentino/langgraph4j/commit/4ec85e7623b69c2ddbc1676e884ac829796131ee))
     > resolve #65


### Documentation

 -  update documentation for new release ([6022e838e8d3de5](https://github.com/bsorrentino/langgraph4j/commit/6022e838e8d3de5b43120cb042d17ac8695f0694))

 -  update changeme ([1e94eb6115e1680](https://github.com/bsorrentino/langgraph4j/commit/1e94eb6115e1680c358a49daf191f168c72b6dfb))


### Refactor

 -  **core**  update dependency version ([c85f4928a5301e0](https://github.com/bsorrentino/langgraph4j/commit/c85f4928a5301e07218fd11da8f8068682ecd968))
    > Updated the &#x60;async-generator-jdk8&#x60; to &#x60;async-generator&#x60; and bumped its version from 2.3.0 to 3.0.0 to incorporate new features and potential bug fixes.

 -  **maven-config**  update maven-javadoc-plugin configuration ([6647247eb331e62](https://github.com/bsorrentino/langgraph4j/commit/6647247eb331e62afde241c7569b5bc682590f32))
    > This commit updates the maven-javadoc-plugin configuration to ensure it does not fail on warnings or errors and disables doclint. The changes are applied
 > across all instances of the plugin configuration.


### ALM 

 -  bump to version 1.2.4 ([d3e201a3e4aa069](https://github.com/bsorrentino/langgraph4j/commit/d3e201a3e4aa06992218b5d28fba2222f5cc1aa3))
   
 -  update dev task accordly ([6551538f6b8273c](https://github.com/bsorrentino/langgraph4j/commit/6551538f6b8273c8b491e3298006a7e4bccc9af1))
   
 -  bump to SNAPSHOT ([c9d43a8ba839d73](https://github.com/bsorrentino/langgraph4j/commit/c9d43a8ba839d73f8b796bc4e1b51d84a92218d2))
   






<!-- "name: v1.2.3" is a release tag -->

## [v1.2.3](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.2.3) (2025-01-13)


### Bug Fixes

 -  **CompiledGraph.java**  update state handling to assume subgraph returns complete state ([3a53ae30eca8ad1](https://github.com/bsorrentino/langgraph4j/commit/3a53ae30eca8ad118ff7ced05fe459086d760030))
     > Refactors &#x60;CompiledGraph&#x60; to update state handling by assuming that the subgraph returns a complete state.


### Documentation

 -  bump new version ([af0af71cffc60b5](https://github.com/bsorrentino/langgraph4j/commit/af0af71cffc60b5bbea57abd4dd5a51cba5f2aa9))

 -  update notebook md format ([76abfb3a680f80e](https://github.com/bsorrentino/langgraph4j/commit/76abfb3a680f80e4387f475b9229c3395fd1a8d5))

 -  update notebooks documentation ([89cfeb06240e8bc](https://github.com/bsorrentino/langgraph4j/commit/89cfeb06240e8bc662bb75a79e3fa369f15b0829))

 -  add javadoc ([c22714110122476](https://github.com/bsorrentino/langgraph4j/commit/c2271411012247602debd68475fe37292cba3355))

 -  update Javadoc ([0bbe23b77c9e69c](https://github.com/bsorrentino/langgraph4j/commit/0bbe23b77c9e69c84382f38cf4b74a4ac95ff78d))

 -  update changeme ([a67842e22c4f653](https://github.com/bsorrentino/langgraph4j/commit/a67842e22c4f6532fc8abe98a00a7084a1b9a68f))


### Refactor

 -  **StateGraph.java**  the `addSubgraph(CompiledGraph)` method is no longer deprecated. ([2bf3126ae4b16e2](https://github.com/bsorrentino/langgraph4j/commit/2bf3126ae4b16e2e35ca8acc48d85810bb845679))
   
 -  **Nodej**  Node Action Factory Interface ([6d00585968efcd7](https://github.com/bsorrentino/langgraph4j/commit/6d00585968efcd721c1623b35a8668f87643368d))
    > Reformatted the &#x60;Node&#x60; record to use an interface for action factory implementation.

 -  **DiagramGenerator**  add graph state exception handling and update node action factory ([7a76fdb0e834254](https://github.com/bsorrentino/langgraph4j/commit/7a76fdb0e834254b855885a18805c0b62f2621af))
   
 -  **CompiledGraph.java**  change constructor to throw GraphStateException ([c70b5c3f27d3aa9](https://github.com/bsorrentino/langgraph4j/commit/c70b5c3f27d3aa9b4244d31f04ebf4f74031fb6e))
   

### ALM 

 -  bump new version ([4c4edc0af587b58](https://github.com/bsorrentino/langgraph4j/commit/4c4edc0af587b58f3ad5121a6b4f313c4f5ae2d6))
   
 -  bump version to SNAPSHOT ([78acd8a4ea21266](https://github.com/bsorrentino/langgraph4j/commit/78acd8a4ea2126616644603c21d0c659b662ee15))
   

### Test 

 -  update llm-straming notebook ([6eb924591ae348d](https://github.com/bsorrentino/langgraph4j/commit/6eb924591ae348de65c7a3480e7f908d8ac21251))
   
 -  **how-tos**  add complete streaming example in notebook ([c2e517fd4377ef5](https://github.com/bsorrentino/langgraph4j/commit/c2e517fd4377ef554698e70e1f31e70ca6078c5a))
   
 -  **how-tos**  add streaming test ([e7bf6c7649868f7](https://github.com/bsorrentino/langgraph4j/commit/e7bf6c7649868f7172d3491f97e99efac707ac3d))
   
 -  **SubGraphText**  add a test class for SubGraph unit test ([984628bda6d1ab3](https://github.com/bsorrentino/langgraph4j/commit/984628bda6d1ab34719b77afed524dae6294a83b))
   
 -  set logging options for unit test ([c576c796595908c](https://github.com/bsorrentino/langgraph4j/commit/c576c796595908c4f22e8902f0e493606fe99e8a))
   





<!-- "name: v1.2.2" is a release tag -->

## [v1.2.2](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.2.2) (2025-01-10)


### Bug Fixes

 -  **core**  conditionally use input based on checkpointSaver presence ([3291e6485e199f1](https://github.com/bsorrentino/langgraph4j/commit/3291e6485e199f122ac8f90215119134d3b06407))
     > - refactored async generator handling to conditionally use &#x60;state.data()&#x60; or an empty map (&#x60;Map.of()&#x60;) based on the presence of &#x60;checkpointSaver&#x60;
     > work on #60


### Documentation

 -  update readme ([cfbd6fc01375103](https://github.com/bsorrentino/langgraph4j/commit/cfbd6fc013751038b132cd78d5d9830d9db442c6))

 -  update changeme ([7dd3183b80c7a52](https://github.com/bsorrentino/langgraph4j/commit/7dd3183b80c7a52f062d14bb3ced67e1ac9cfb90))


### Refactor

 -  **CompiledGraph.java**  update node action initialization ([a6467614f519946](https://github.com/bsorrentino/langgraph4j/commit/a6467614f5199461a2a6d3e1a4f295402a48df39))
    > Refactored the way node actions are initialized to use an action factory from the &#x60;StateNode&#x60; and apply it with the compile configuration. Ensured that each node has a non-null action factory before proceeding.
 > BREAKING CHANGE: The previous approach of directly associating nodes with their actions without factories is deprecated.
 > work on #60

 -  **StateGraph**  update addSubgraph method behavior using actionFactory ([fffce294a657cf9](https://github.com/bsorrentino/langgraph4j/commit/fffce294a657cf9faf09c0759d4a922303469eca))
    > - Deprecate the &#x60;addSubgraph(CompiledGraph)
 > - Add &#x60;addSubgraph(StateGraph)&#x60;
 > work on #60

 -  **DiagramGenerator.java**  Refactored subgraph node action handling to use factory method ([ac0e989ad7fa674](https://github.com/bsorrentino/langgraph4j/commit/ac0e989ad7fa674d11d2d73bab37aee1be398990))
    > work on #60

 -  **Node.java**  refine class structure and add factory method ([56474015bdacf5f](https://github.com/bsorrentino/langgraph4j/commit/56474015bdacf5fada8fcf7f8b742e913957651d))
    > - Update class to be a record for improved immutability and simplicity.
 > - Replace constructor overloads with a single constructor for &#x60;id&#x60; only, using a lambda expression for optional action factory.
 > - Remove deprecated fields and methods (&#x60;action&#x60; field, redundant constructors).
 > work on #60

 -  update SubgraphNodeAction constructor and method parameters ([e5254216017a072](https://github.com/bsorrentino/langgraph4j/commit/e5254216017a072bdd765071559927af349e7e4b))
    > Refactored &#x60;SubgraphNodeAction&#x60; to accept &#x60;StateGraph&lt;State&gt;&#x60; and &#x60;CompileConfig&#x60; instead of just &#x60;CompiledGraph&lt;State&gt;&#x60;. Updated the method parameter for the stream call to use a default empty map instead of state data.
 > work on #60

 -  **StateGraphTest.java**  add debugging output to workflow stream ([1582432e6b5040f](https://github.com/bsorrentino/langgraph4j/commit/1582432e6b5040fbae5c019c35f39bf13d640199))
   
 -  **Channel.java**  add type casting suppression in update method ([6e3ff39cae4d930](https://github.com/bsorrentino/langgraph4j/commit/6e3ff39cae4d930521f5975df158e94fe195714e))
   
 -  **CompiledGraph.java**  optimize invoke method ([b55be86be761949](https://github.com/bsorrentino/langgraph4j/commit/b55be86be761949629c2aee28689663c9f2de112))
    > Refactored the &#x60;invoke&#x60; method in &#x60;CompiledGraph.java&#x60; to streamline the reduction process. This change reduces the overhead by eliminating unnecessary intermediate collections and stream operations.


### ALM 

 -  bump version to 1.2.2 ([d2c19b620c4786b](https://github.com/bsorrentino/langgraph4j/commit/d2c19b620c4786b9e360054c4c75f552ef6fa136))
   
 -  bump version to SNAPSHOT ([deec2e9c480cfe5](https://github.com/bsorrentino/langgraph4j/commit/deec2e9c480cfe534b258bf7e9770ef78355cc00))
   

### Test 

 -  creation of test cases for workflows that integrate subgraphs. ([ada1e3df6333254](https://github.com/bsorrentino/langgraph4j/commit/ada1e3df633325447d9f7e69a7c63bd184189031))
   





<!-- "name: v1.2.1" is a release tag -->

## [v1.2.1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.2.1) (2025-01-08)

### Features

 *  merge pull request #59 ([c9baa6c47ce4d6d](https://github.com/bsorrentino/langgraph4j/commit/c9baa6c47ce4d6d4075efec1daa0b86745a69656))
     > fix issue #55
   

### Bug Fixes

 -  **README.md**  update dependency version ([128f5f080d54cd7](https://github.com/bsorrentino/langgraph4j/commit/128f5f080d54cd74131c48d6daccb9ccc6cc6a01))
     > The commit updates the dependency version in the README.md file to 1.2.0, ensuring that users can easily install the correct version of the LangGraph for Java library.
     > - CHANGELOG: incremented langgraph4j-core dependency version

 -  update javadoc ([3ed265942dabb13](https://github.com/bsorrentino/langgraph4j/commit/3ed265942dabb13de954695b9085c370c77c0946))


### Documentation

 -  update changeme ([c306defe68d2f7c](https://github.com/bsorrentino/langgraph4j/commit/c306defe68d2f7cd20b2e68dbba583c41e224c80))



### ALM 

 -  bump version to 1.2.1 ([3ee702eb088553f](https://github.com/bsorrentino/langgraph4j/commit/3ee702eb088553f712295f666e1bbd8adf185d17))
   
 -  bump version to 1.2.1 ([0f35d0a934a48d7](https://github.com/bsorrentino/langgraph4j/commit/0f35d0a934a48d7f342caae4956932c7c7b80193))
   
 -  update version to SNAPSHOT ([ba8e5fb20e8fa3b](https://github.com/bsorrentino/langgraph4j/commit/ba8e5fb20e8fa3b1ac072cfb430c0777807149f6))
    > Bump the project version from 1.2.0 to 1.2-SNAPSHOT, preparing for development of the next release cycle.

 -  update version to SNAPSHOT ([1cd97afed49a531](https://github.com/bsorrentino/langgraph4j/commit/1cd97afed49a5311689f3be9eac561e284c01b7d))
    > Bump the project version from 1.2.0 to 1.2-SNAPSHOT, preparing for development of the next release cycle.


### Test 

 -  **core**  add subgraph unit test ([5837687bde1ed94](https://github.com/bsorrentino/langgraph4j/commit/5837687bde1ed9400e7f4843a6873f8a234bc5d2))
   
 -  **subgraph.ipynb**  relaunch notebook ([c0372d2b7f42a66](https://github.com/bsorrentino/langgraph4j/commit/c0372d2b7f42a66fb764bb1fc9e709ea3062686f))
   





<!-- "name: v1.2.0" is a release tag -->

## [v1.2.0](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.2.0) (2025-01-05)

### Features

 *  add sample ([c74eee01cd6cd47](https://github.com/bsorrentino/langgraph4j/commit/c74eee01cd6cd47911909fe0ed74a3710aabf98c))
     > work on #51
   

### Bug Fixes

 -  **notebook**  use langgraph4j-core  instead of  langgraph4j-core-jdk8 ([efdd9fd78d38257](https://github.com/bsorrentino/langgraph4j/commit/efdd9fd78d38257562fac97b51187e5f03da3236))


### Documentation

 -  update notebook md documents ([13b4e72804efbe1](https://github.com/bsorrentino/langgraph4j/commit/13b4e72804efbe14d903a9a803653cec8bb4d5e7))

 -  bump to new version ([f521c6bf08359e6](https://github.com/bsorrentino/langgraph4j/commit/f521c6bf08359e6b8c2c2651b6468df0279185ab))

 -  **readme**  add release note ([63ecd62cf44ef74](https://github.com/bsorrentino/langgraph4j/commit/63ecd62cf44ef74ced719f9c2947e83dfa6584ec))
     > resolve #54

 -  improve javadoc ([a6408c326771fdc](https://github.com/bsorrentino/langgraph4j/commit/a6408c326771fdcf1a276cfd78469190be09dc33))

 -  **EvaluateResult.java**  improve javadoc ([90dea651e617b5a](https://github.com/bsorrentino/langgraph4j/commit/90dea651e617b5a389ae9f1a81b17d93c42be026))

 -  fix javadoc comments ([4f75dad7865f046](https://github.com/bsorrentino/langgraph4j/commit/4f75dad7865f046eaa255093bc7035ea8a63077e))

 -  javadoc refinements ([c07914f61418ef5](https://github.com/bsorrentino/langgraph4j/commit/c07914f61418ef538dacb227161930a81318e1e5))

 -  javadoc refinements ([d8f9a4f6131cc5b](https://github.com/bsorrentino/langgraph4j/commit/d8f9a4f6131cc5b39642c98e4c5762c9009dc36d))

 -  javadoc refinements ([f412c7a6b4396dd](https://github.com/bsorrentino/langgraph4j/commit/f412c7a6b4396dd4c96bc68ce3c9a7314ab049cb))

 -  javadoc refinements ([d3c87ebc620eb93](https://github.com/bsorrentino/langgraph4j/commit/d3c87ebc620eb93e7389966078c53e9e93b755a8))

 -  added javadoc ([e77d61cfcfed82c](https://github.com/bsorrentino/langgraph4j/commit/e77d61cfcfed82c3e6e91a909bfae62d249ca34d))

 -  added javadoc ([e1e32d18eac0ceb](https://github.com/bsorrentino/langgraph4j/commit/e1e32d18eac0cebc56df269d924f0ff7df252bad))

 -  added javadoc ([0786adb2b4f420d](https://github.com/bsorrentino/langgraph4j/commit/0786adb2b4f420d5726edf756aa173f82b2f10ab))

 -  **GeneratePlantUMLMindmap.java**  add Javadocs and improve implementation ([d25fa3347146a5d](https://github.com/bsorrentino/langgraph4j/commit/d25fa3347146a5d064c613f135a8aeb1875a3bbf))
     > - Add JavaDoc annotations to describe the class and its methods for better readability and maintainability.
     > - Improve code structure and readability by refactoring the constructor and apply method.

 -  added comprehensive documentation ([5a5020ed175dd69](https://github.com/bsorrentino/langgraph4j/commit/5a5020ed175dd69774da2f998f0a05cb915a6fc3))

 -  **AgenticFlow.java**  update Javadoc comments a ([c68cd27c527da40](https://github.com/bsorrentino/langgraph4j/commit/c68cd27c527da40ecf2f08b30fb0aa928a3b558a))

 -  added comprehensive documentation ([f000b914754c056](https://github.com/bsorrentino/langgraph4j/commit/f000b914754c056005b184b7eaa107e6a860ed9e))

 -  added comprehensive documentation ([6d9219a7cb12e1c](https://github.com/bsorrentino/langgraph4j/commit/6d9219a7cb12e1c6920270b0467e6daff32cc707))

 -  added comprehensive documentation ([eb4a1ac87b37d43](https://github.com/bsorrentino/langgraph4j/commit/eb4a1ac87b37d431dcce0bcfab3c7339e733d5c8))

 -  added comprehensive documentation ([516830b7f835ad8](https://github.com/bsorrentino/langgraph4j/commit/516830b7f835ad811bffed0a846c02ffbde492cc))

 -  added comprehensive documentation ([b731612bd46284a](https://github.com/bsorrentino/langgraph4j/commit/b731612bd46284ab8a9f0aef7b7d2e0604665ee7))

 -  added comprehensive documentation ([98868079720ad98](https://github.com/bsorrentino/langgraph4j/commit/98868079720ad98f53dacf93ab29d391c65b1988))

 -  **AgentExecutor.java**  added comprehensive documentation ([8ff0cc8a4c175c1](https://github.com/bsorrentino/langgraph4j/commit/8ff0cc8a4c175c182f082136f4998bc47283bcd8))

 -  **Agent.java**  add JavaDoc comments for better code readability and maintainability ([17386f5761cea4b](https://github.com/bsorrentino/langgraph4j/commit/17386f5761cea4b7d65e03b53e1f306ee9246845))

 -  **ExecuteTools.java**  enhance class and method documentation, update constructor with non-null constraints, and improve error handling ([0b0424d3bfe178c](https://github.com/bsorrentino/langgraph4j/commit/0b0424d3bfe178c3bd6e7bbe4059c3413533e5b0))

 -  **CallAgent.java**  add comprehensive Javadoc comments and method descriptions ([48c6c8c589ede5c](https://github.com/bsorrentino/langgraph4j/commit/48c6c8c589ede5c0747f2ddc43c39fda26871495))

 -  update readme ([ac3e230b4b49e48](https://github.com/bsorrentino/langgraph4j/commit/ac3e230b4b49e48d20d309bc0ce89a14d125ab32))

 -  update changeme ([c2b558473ff5059](https://github.com/bsorrentino/langgraph4j/commit/c2b558473ff5059949f2d45940bdff4a30422ae6))


### Refactor

 -  **notebook**  upgrade langgraph4j version ([253d93274c389c5](https://github.com/bsorrentino/langgraph4j/commit/253d93274c389c5e67e8f37aa39814cf1055b639))
   
 -  **notebook**  upgrade langgraph4j version ([64de3761fc625b0](https://github.com/bsorrentino/langgraph4j/commit/64de3761fc625b0b1dba555411a956410205ff83))
   
 -  remove redundant JDK-8 version configuration ([d19a8c1be8e75c4](https://github.com/bsorrentino/langgraph4j/commit/d19a8c1be8e75c4951ff54dedeb858dc07980b7c))
    > - refactor(set-version.sh)
 > - refactor(site-run.sh)
 > work on #54

 -  remove redundant JDK-8 version configuration ([3bdf67a05fb4b0d](https://github.com/bsorrentino/langgraph4j/commit/3bdf67a05fb4b0d4d460874e9d09e43d32856c5f))
    > - refactor(deploy-pages.yml)
 > - refactor(deploy-snapshot.yaml)
 > - refactor(deploy.yaml)
 > work on #54

 -  **core**  rename module core-jdk8 to core ([58b366d81ad7fe3](https://github.com/bsorrentino/langgraph4j/commit/58b366d81ad7fe348ecb6d7bfe8615be99379b0b))
    > work on #54

 -  **core**  rename langgraph4j-core-jdk8 to langgraph4j-core ([db3b5224865e82a](https://github.com/bsorrentino/langgraph4j/commit/db3b5224865e82a9211ff6fa51ab39532ace30f2))
    > work on #54

 -  **AgentFunctionCallbackWrapper.java**  Annotate methods with @SuppressWarnings("unchecked") to suppress compiler warnings. ([a28b7d55d837e1d](https://github.com/bsorrentino/langgraph4j/commit/a28b7d55d837e1df67dd08bd46d88894d60860bf))
   
 -  **CallAgent.java**  remove unused imports and unnecessary imports ([3e861ce02b99202](https://github.com/bsorrentino/langgraph4j/commit/3e861ce02b992029834d8498b5a05c745e96ea1a))
   

### ALM 

 -  bump version to 1.2.0 ([f01f97e3d585bda](https://github.com/bsorrentino/langgraph4j/commit/f01f97e3d585bda63023ec5f5135bd6869de8326))
   
 -  bump version to 1.2-SNAPSHOT ([2c82eaaa02dbd8b](https://github.com/bsorrentino/langgraph4j/commit/2c82eaaa02dbd8bed0ca21b472184c0e6a7453fe))
   
 -  bump javadoc plugin ([490a69d5184f368](https://github.com/bsorrentino/langgraph4j/commit/490a69d5184f368fd687d1747ca47fb4af11da7f))
   
 -  bump version to 1.2.0-SNAPSHOT ([c7b7fc130296de4](https://github.com/bsorrentino/langgraph4j/commit/c7b7fc130296de4ab48569917dec0d47f936b65a))
   
 -  update `spring-ai.version` to 1.0.0-M4 ([5b6e9ddc951665b](https://github.com/bsorrentino/langgraph4j/commit/5b6e9ddc951665b7bdb58e4f1c214d15d8f4b77c))
   
 -  **pom**  update version to 1.1-SNAPSHOT ([cb5c57108456537](https://github.com/bsorrentino/langgraph4j/commit/cb5c571084565372796d83c4b8f2f37f7b514e7e))
    > - Updated child modules&#x27; versions to reflect parent-pom version
 > - Bumped parent-pom version to 1.1-SNAPSHOT







<!-- "name: v1.1.5" is a release tag -->

## [v1.1.5](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.1.5) (2024-12-14)



### Documentation

 -  bump version in documentation ([61394a3779ce24b](https://github.com/bsorrentino/langgraph4j/commit/61394a3779ce24b14129ee9ad5e8dd0e80a22cde))

 -  **agent-executor**  update documentation ([2cd1e63cf3c8659](https://github.com/bsorrentino/langgraph4j/commit/2cd1e63cf3c8659f81ee1492234c9bbfb9b9af09))

 -  Documented addition of TestTool and dynamic executor ([e87feebc6ed406f](https://github.com/bsorrentino/langgraph4j/commit/e87feebc6ed406fa022e95174b6cf229a34f3815))

 -  update changeme ([4aa1b232dbf14a9](https://github.com/bsorrentino/langgraph4j/commit/4aa1b232dbf14a921c6d4d5e543cac42edd277e0))


### Refactor

 -  **agent-executor**  remove unused maven plugins ([90734fd33818a98](https://github.com/bsorrentino/langgraph4j/commit/90734fd33818a9822bc49d78c7afdadade69942a))
    > - Removed &#x60;maven-site-plugin&#x60;, &#x60;maven-deploy-plugin&#x60;, and &#x60;maven-surefire-plugin&#x60; configurations that were unnecessarily skipping steps.
 > .

 -  **pom.xml**  add option skipStagingRepositoryClose ([bdba67d91c414c8](https://github.com/bsorrentino/langgraph4j/commit/bdba67d91c414c89366ab7047bed9dbf6a74fa07))
   
 -  **agent-executor**  promote agent executor as main package ([5e2992db8146368](https://github.com/bsorrentino/langgraph4j/commit/5e2992db81463689b3dd455a34d2cfe9f706155f))
   

### ALM 

 -  bump version ([74f4532109977ec](https://github.com/bsorrentino/langgraph4j/commit/74f4532109977ec424b5fce8b195db7f1befc6a5))
   
 -  bump version ([e012329d16d95c8](https://github.com/bsorrentino/langgraph4j/commit/e012329d16d95c8b48ea03282f86be3828910d56))
   
 -  update project to use latest version ([da4e05ed598b7c3](https://github.com/bsorrentino/langgraph4j/commit/da4e05ed598b7c365f794bec0030ed5eb7117bd7))
   






<!-- "name: v1.1.4" is a release tag -->

## [v1.1.4](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.1.4) (2024-12-11)



### Documentation

 -  bump version to 1.1.4 ([8b3412603a77405](https://github.com/bsorrentino/langgraph4j/commit/8b3412603a7740525bde55dcb6252a2459d57640))

 -  update documentation ([9c31acd3d69d1c8](https://github.com/bsorrentino/langgraph4j/commit/9c31acd3d69d1c833b4fd62055afd4af0d81969c))

 -  update documentation ([88bc458aa49026f](https://github.com/bsorrentino/langgraph4j/commit/88bc458aa49026f0c5f27ae0dd02a8621637677c))

 -  update changeme ([bca96c976c52505](https://github.com/bsorrentino/langgraph4j/commit/bca96c976c525056cc5a78c79a39ec211e406a8b))


### Refactor

 -  **notebook**  bump langgraph4j version ([e73e40cd900d9da](https://github.com/bsorrentino/langgraph4j/commit/e73e40cd900d9daa09d4c3af98bd99ae9eecdff3))
   
 -  **AgentExecutor**  disable agent executor test by default ([f7d5f6b109eb901](https://github.com/bsorrentino/langgraph4j/commit/f7d5f6b109eb901ca64080c06696638a2604d366))
   

### ALM 

 -  bump version to 1.1.4 ([141e81b4f4de729](https://github.com/bsorrentino/langgraph4j/commit/141e81b4f4de7291ca3dcd61691b37ae5ec63af7))
   
 -  update doxia-module-markdown version to latest stable ([595c124979917df](https://github.com/bsorrentino/langgraph4j/commit/595c124979917dffe08cce661ffab0281a28e79b))
    > - Updated the version of the doxia-module-markdown dependency from 2.0.0-M12 to 2.0.0 to address any known issues and ensure compatibility with recent projects.

 -  bump to new SNAPSHOT ([0333b25987a3293](https://github.com/bsorrentino/langgraph4j/commit/0333b25987a3293d6232fa75361ce88cd329dfce))
   
 -  **AgentExecutor**  add support for Azure OpenAI using langchain4j ([3d7a7a68dd5f8ce](https://github.com/bsorrentino/langgraph4j/commit/3d7a7a68dd5f8ce3502c604a0857ed457716c148))
    > work on #50


### Test 

 -  **AgentExecutor**  refactor unit test for supporting both OpneAI and AzureOpneAI ([0e93aa132eb30b2](https://github.com/bsorrentino/langgraph4j/commit/0e93aa132eb30b2f7829f4f925d9418736697348))
    > resolve #50






<!-- "name: v1.1.3" is a release tag -->

## [v1.1.3](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.1.3) (2024-12-03)

### Features

 *  **studio**  add subgraph management ([088b2ddaaec153b](https://github.com/bsorrentino/langgraph4j/commit/088b2ddaaec153ba573ff1bacb254d2eb8e9cf17))
     > - remove unnecessary ObjectMapper injection
     > - Refactored GraphStreamServlet to no longer require an objectMapper, simplifying dependency management.
     > - Removed redundant ObjectMapper configuration in LangGraphStreamingServerJetty builder.
   
 *  add subGraph field to NodeOutput class ([06a4acc4f6764a4](https://github.com/bsorrentino/langgraph4j/commit/06a4acc4f6764a4613ffdd20a586918d07388aab))
   
 *  **image-to-diagram**  handle imageData as JSONPrimitive or JsonObject ([4e114e84139a59f](https://github.com/bsorrentino/langgraph4j/commit/4e114e84139a59fe9f2859c9199221efb2551bf4))
   
 *  **studio**  add image-to-diagram workflow ([390dc819a52ef89](https://github.com/bsorrentino/langgraph4j/commit/390dc819a52ef897192e372c6a71df215d7e60ad))
   
 *  **studio**  update argument metadata to use enums and ([f95bd80e7afb9c5](https://github.com/bsorrentino/langgraph4j/commit/f95bd80e7afb9c57103933694796ff68dedfbe0e))
     > lists
   
 *  **studio**  Add a new component for image upload ([946ba93b0176021](https://github.com/bsorrentino/langgraph4j/commit/946ba93b0176021c75c3a48a839c4084a6d8566f))
   

### Bug Fixes

 -  **studio**  classloader error on loading embed resources ([d317e75a2f504b0](https://github.com/bsorrentino/langgraph4j/commit/d317e75a2f504b027433fb215183527b4086ecbf))

 -  **StateDeserializer**  correct imageDataElement handling ([cc4f56dc8e5244f](https://github.com/bsorrentino/langgraph4j/commit/cc4f56dc8e5244f5745a4dbe26aa206df47a43e6))

 -  **studio**  remove test attribute ([045cb7a8737928a](https://github.com/bsorrentino/langgraph4j/commit/045cb7a8737928a8f320adf4900526f74d6eb76d))


### Documentation

 -  update changeme ([e6b36ba303586ff](https://github.com/bsorrentino/langgraph4j/commit/e6b36ba303586ffcf17acf6945824bc02e4836e1))


### Refactor

 -  **studio**  remove unused ObjectMapper from AgentExecutorStreamingServer ([a7d05a750a24fa9](https://github.com/bsorrentino/langgraph4j/commit/a7d05a750a24fa920250663b2406d7a1ea51877e))
   
 -  **CompiledGraph**  set subGraph flag to async generator output ([8d11886d1cc6b0a](https://github.com/bsorrentino/langgraph4j/commit/8d11886d1cc6b0a50ffe6c2fd0ecbac6cbd5cfbb))
   
 -  **MermaidGenerator**  make SUBGRAPH_PREFIX final and public ([e65f75709765cea](https://github.com/bsorrentino/langgraph4j/commit/e65f75709765cea35a330ef50abc4fdaed59d200))
   
 -  **studio**  update base64 encoding method in lg4j-image-uploader.js ([ce34c5183fd1c4f](https://github.com/bsorrentino/langgraph4j/commit/ce34c5183fd1c4fa8de7b10a0fb39c4d687ab0bc))
   
 -  **lg4j-image-uploader**  convert file to Base64 and update value method ([fa8f5b211a90a00](https://github.com/bsorrentino/langgraph4j/commit/fa8f5b211a90a004c5cb7bb009bedd50ac54d446))
   
 -  **diagram**  refine mermaid subgraph generation ([ab720c24a96702f](https://github.com/bsorrentino/langgraph4j/commit/ab720c24a96702f083875c05a5c6a9997d902dc2))
   

### ALM 

 -  bump new version ([e5d8cb3d4d9f023](https://github.com/bsorrentino/langgraph4j/commit/e5d8cb3d4d9f023062fa253af619d36b68893612))
   
 -  **async-generator**  update dependency version ([a0b66e3f468c7fa](https://github.com/bsorrentino/langgraph4j/commit/a0b66e3f468c7fa4a8a1bd953c975dc4c01317df))
   
 -  **frontend**  update dist ([1888f5bb402c16a](https://github.com/bsorrentino/langgraph4j/commit/1888f5bb402c16ada3d4325be2a773551932ae7c))
   
 -  **frontend**  update dist ([7025fda56c03f48](https://github.com/bsorrentino/langgraph4j/commit/7025fda56c03f483fcead5456ed639f37ff56dc1))
   
 -  **studio**  update dependencies and types ([1910d0fe1ff5f76](https://github.com/bsorrentino/langgraph4j/commit/1910d0fe1ff5f76b7dc0e47f4519a0a2009cadeb))
   
 -  update versions to SNAPSHOT across all modules ([5465d89c19776b9](https://github.com/bsorrentino/langgraph4j/commit/5465d89c19776b9bff9754b30400283864e72a79))
   

### Test 

 -  **GsonSerializer**  unit test refinements ([2d160c2bae0adbb](https://github.com/bsorrentino/langgraph4j/commit/2d160c2bae0adbb22040ffaf380b21a9c4d71cc3))
   
 -  **image-to-diagram**  refine unit test ([59150ac455a0dfc](https://github.com/bsorrentino/langgraph4j/commit/59150ac455a0dfc521cb4eb27d466749dc9b2438))
   





<!-- "name: v1.1.2" is a release tag -->

## [v1.1.2](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.1.2) (2024-11-29)

### Features

 *  **image-to-diagram**  update diagrams and workflow for image processing ([778c8cc5fe3c669](https://github.com/bsorrentino/langgraph4j/commit/778c8cc5fe3c6691b36bc8a8f9acf311fc95bc0e))
   
 *  add notebook for subgraph sample ([26f9993f56d114d](https://github.com/bsorrentino/langgraph4j/commit/26f9993f56d114dc2e80030a6dea9b23cec65701))
   
 *  add support of subgraph in graph representation generation ([99ff7b4761a1cd8](https://github.com/bsorrentino/langgraph4j/commit/99ff7b4761a1cd8319292534dc577ade05ca4e6c))
     > - streamline diagram generation with Context class
   


### Documentation

 -  update with new version ([44fb415f1c3f919](https://github.com/bsorrentino/langgraph4j/commit/44fb415f1c3f9192ad4b301c0482f4ef8d273233))

 -  update changeme ([180e4389e611ed8](https://github.com/bsorrentino/langgraph4j/commit/180e4389e611ed810cecac234e808ba8567f10b7))



### ALM 

 -  bump to next version ([762637ba25974b0](https://github.com/bsorrentino/langgraph4j/commit/762637ba25974b0a1056e1d92fc13039ba3825b7))
   
 -  update version to 1.2-SNAPSHOT for modules ([0636130c2f05495](https://github.com/bsorrentino/langgraph4j/commit/0636130c2f05495da3b9109dca95a5586105e132))
   






<!-- "name: v1.1.1" is a release tag -->

## [v1.1.1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.1.1) (2024-11-26)

### Features

 *  makes AsyncNodeActionWithConfig the standard action ([a52695de667ea22](https://github.com/bsorrentino/langgraph4j/commit/a52695de667ea2233fe8a5cc639c89f084c18e2b))
   

### Bug Fixes

 -  **subgraph**  makes AsyncNodeActionWithConfig the standard action and acceps null result from embed generator ([68228a2d1481a0e](https://github.com/bsorrentino/langgraph4j/commit/68228a2d1481a0ebf7d19fce71f132fe4f08994a))


### Documentation

 -  update changeme ([25d84a5d54c0def](https://github.com/bsorrentino/langgraph4j/commit/25d84a5d54c0deff2cd9a691e8e0680508c183af))


### Refactor

 -  deprecated state methods: ([835b7360d24aa2b](https://github.com/bsorrentino/langgraph4j/commit/835b7360d24aa2bc7b43aca443b9a46b2d94f4c0))
    > value( key, T  )
 > value( key, Supplier&lt;T&gt; )

 -  **agent-executor**  update actions management ([e2fb377da0d944b](https://github.com/bsorrentino/langgraph4j/commit/e2fb377da0d944b4b9bfae79cfa9e5e42420221b))
   

### ALM 

 -  bump to next release ([3cff8a5ee5630ff](https://github.com/bsorrentino/langgraph4j/commit/3cff8a5ee5630ff8451aeecd07499dd97a4a54a2))
   
 -  bump next SNAPSHOT ([c7a966f4c78278a](https://github.com/bsorrentino/langgraph4j/commit/c7a966f4c78278af008f0988df14d007cc9d9e8c))
   

### Test 

 -  **subgraph**  adds a notebbok ([d619829c2240a9e](https://github.com/bsorrentino/langgraph4j/commit/d619829c2240a9e66eaefb265b035e2c8000cd09))
   





<!-- "name: v1.1.0" is a release tag -->

## [v1.1.0](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.1.0) (2024-11-26)

### Features

 *  adds subgraph  action support in state graph ([38988780cf22cb6](https://github.com/bsorrentino/langgraph4j/commit/38988780cf22cb612d8a32642a592032d0ca5702))
     > resolve  #39
   
 *  makes compliant with new node action that accepts RunnableConfig ([c640f3aa8f582ec](https://github.com/bsorrentino/langgraph4j/commit/c640f3aa8f582ec3ad9cd7837d6cb57e192acc30))
     > work on #39
   
 *  adds subgraph node action ([266fe4a2d3f51d8](https://github.com/bsorrentino/langgraph4j/commit/266fe4a2d3f51d8366ace38c45b12281afd28eae))
     > work on #39
   
 *  adds node action that accept either Map and RunnableConfig ([aaf57f15199df40](https://github.com/bsorrentino/langgraph4j/commit/aaf57f15199df40aa0ac67320a5b7f703dd182b7))
     > work on #39
   
 *  refine jackson & gson serialization impl ([8ebd7df555a9180](https://github.com/bsorrentino/langgraph4j/commit/8ebd7df555a918029d4280d9af0efb7076839f8d))
     > - update agent executor serialization impl
     > - update image to diagram serailization impl
   
 *  refine jackson & gson serialization impl ([a7e1619b3e1feb1](https://github.com/bsorrentino/langgraph4j/commit/a7e1619b3e1feb11363e1c6489c278193bf6a2f2))
     > - update agent executor serialization impl
     > - update image to diagram serailization impl
   


### Documentation

 -  update release tag ([ebc75ba5f23a250](https://github.com/bsorrentino/langgraph4j/commit/ebc75ba5f23a250aae5d7da8f982dd0f466a84dc))

 -  update readme ([53ee9e49ac9d8ba](https://github.com/bsorrentino/langgraph4j/commit/53ee9e49ac9d8ba02525cdce9fc7d97a093e67ec))

 -  **langchain4j**  update readme ([1a795510260139a](https://github.com/bsorrentino/langgraph4j/commit/1a795510260139aed3992a7758a9e1e546382d4b))

 -  **langchain4j**  update readme ([507fbd2b39641b7](https://github.com/bsorrentino/langgraph4j/commit/507fbd2b39641b76f3da3f5a7155c20f6500ca24))

 -  **langchain4j**  update readme ([96fa8d5ee0d97aa](https://github.com/bsorrentino/langgraph4j/commit/96fa8d5ee0d97aaceefb9ed87bd25a4b223ace54))

 -  update changeme ([54b616a96bd439b](https://github.com/bsorrentino/langgraph4j/commit/54b616a96bd439b53c0820f7eefef24baaef13ec))


### Refactor

 -  updates methods names ([d8138ad0f0ab3c4](https://github.com/bsorrentino/langgraph4j/commit/d8138ad0f0ab3c4989ff4ee75a6686bef7cec9cd))
   
 -  **image-to-diagram**  update actions management ([464e37b03b449b4](https://github.com/bsorrentino/langgraph4j/commit/464e37b03b449b4f53b44b53ac690d976c77dfc0))
   
 -  **agent-executor**  update actions management ([ee61b11177b54bd](https://github.com/bsorrentino/langgraph4j/commit/ee61b11177b54bd801752e0eb83da50a5f04755a))
   
 -  **agent-executor**  update project layout ([bd1e35fb47e8c72](https://github.com/bsorrentino/langgraph4j/commit/bd1e35fb47e8c72fd001d684dbe2808790ec7962))
   
 -  adds Node constructors ([63dd250c1635ca1](https://github.com/bsorrentino/langgraph4j/commit/63dd250c1635ca1675a0e63415fe25a9670dace9))
    > work on #39

 -  **imageToDiagram**  move Node Action from method to Class ([6e5632d82967a8f](https://github.com/bsorrentino/langgraph4j/commit/6e5632d82967a8ff2875e51ca82d86e705625b4b))
   
 -  **DiagramGenerator**  remove usage of getFnishPoint() ([39155598281962a](https://github.com/bsorrentino/langgraph4j/commit/39155598281962a776d86aef57e146ff25c073db))
   
 -  **CompiledGraph**  deprecate getEntryPoint() and getFinishPoint() ([2e26d5f462f7732](https://github.com/bsorrentino/langgraph4j/commit/2e26d5f462f7732e90121a218a80c5b5370e2715))
   

### ALM 

 -  bump to next version ([131b605d7449814](https://github.com/bsorrentino/langgraph4j/commit/131b605d7449814c6eae1f658795e45d783a3834))
   
 -  bump to SNAPSHOT ([947f8455cad9cc6](https://github.com/bsorrentino/langgraph4j/commit/947f8455cad9cc66c1d2311f1003c63f742d0e48))
   
 -  move langchain4j integration module on java 17 ([a0a8d4a4b71dba3](https://github.com/bsorrentino/langgraph4j/commit/a0a8d4a4b71dba334699734be3c23acab0fc27a7))
   
 -  **mapifyai**  update project artifact id ([e80eee6d877d9d3](https://github.com/bsorrentino/langgraph4j/commit/e80eee6d877d9d356ce2b4f59b79f727ae7394be))
   

### Test 

 -  **image-to-diagram**  finalize subgraph support ([59f2a056b61c5b0](https://github.com/bsorrentino/langgraph4j/commit/59f2a056b61c5b07828912ea076abe882099d29c))
    > work on #39

 -  **image-to-diagram**  adds subgraph support ([14fc442935588e7](https://github.com/bsorrentino/langgraph4j/commit/14fc442935588e74333960cd45a033a8b948f52d))
    > work on #39






<!-- "name: v1.0.0" is a release tag -->

## [v1.0.0](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0.0) (2024-11-15)



### Documentation

 -  replace new version refs ([a06e5408dece2ec](https://github.com/bsorrentino/langgraph4j/commit/a06e5408dece2ec18edccff0b734c972264bf685))

 -  finalize serializer doc ([24cf52b065306ff](https://github.com/bsorrentino/langgraph4j/commit/24cf52b065306ffbbe813b9c6cfc4090ca737fef))
     > resolve #38

 -  site refinement ([aecd65ef0df55e9](https://github.com/bsorrentino/langgraph4j/commit/aecd65ef0df55e94b14520d15746d7ed149f1447))

 -  update notebooks run ([4ef6c3ae9a45ce9](https://github.com/bsorrentino/langgraph4j/commit/4ef6c3ae9a45ce922f4cc36cf00c14ab10064e94))

 -  update readme ([a6e691aaffebf60](https://github.com/bsorrentino/langgraph4j/commit/a6e691aaffebf6021bd439feaac989dc984a7c8c))



### ALM 

 -  bump new langchain4j version ([0dc4ff72d2c039b](https://github.com/bsorrentino/langgraph4j/commit/0dc4ff72d2c039b4937e7e97c094328af94c20e2))
   
 -  bump to new official release ([7d2867f7d7e27fb](https://github.com/bsorrentino/langgraph4j/commit/7d2867f7d7e27fb932e030a3937ebbbf50b173a1))
   






<!-- "name: v1.0-20241113" is a release tag -->

## [v1.0-20241113](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20241113) (2024-11-13)

### Features

 *  **StreamingOutput**  add toString() ([c8916f44303b350](https://github.com/bsorrentino/langgraph4j/commit/c8916f44303b350ed8da0a62b4dc7c02130cdc44))
   


### Documentation

 -  **core**  update sime menu ([8573a6e830e3d40](https://github.com/bsorrentino/langgraph4j/commit/8573a6e830e3d40f36a2d770bf097251a7a21a44))



### ALM 

 -  bump next intermediate version ([a2da7cc63ab9751](https://github.com/bsorrentino/langgraph4j/commit/a2da7cc63ab975168c4afb1d5ea73703aa9519bd))
   
 -  bump to snapshot ([511fb622550973a](https://github.com/bsorrentino/langgraph4j/commit/511fb622550973a0a1889f903056a8b6559dfa5e))
   

### Test 

 -  add script to build and run site locally ([eb987a529aaa73c](https://github.com/bsorrentino/langgraph4j/commit/eb987a529aaa73c2ec55d7ca1806c33e44ed2fb4))
   





<!-- "name: v1.0-20241112" is a release tag -->

## [v1.0-20241112](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20241112) (2024-11-12)

### Features

 *  **AgentExecutor**  allow to use dynamic tool definirion ([e463bfe87059da7](https://github.com/bsorrentino/langgraph4j/commit/e463bfe87059da7d1984b9e6582c04b23b5d3da1))
   

### Bug Fixes

 -  **PlantUMLgenerator**  use right finish node ([6a7a0b9ed539f83](https://github.com/bsorrentino/langgraph4j/commit/6a7a0b9ed539f83408c1491bbcd7ca067e3a2f3c))


### Documentation

 -  update readme ([04fac5f56576c1c](https://github.com/bsorrentino/langgraph4j/commit/04fac5f56576c1cb373d5fea02eb8fa5dada70ab))
     > resolve #47

 -  update readme ([a16cd11b4f9ea93](https://github.com/bsorrentino/langgraph4j/commit/a16cd11b4f9ea939208c61ed22c1d3b2234638d6))

 -  update agent-executor docs ([2ab48f37b8fc09b](https://github.com/bsorrentino/langgraph4j/commit/2ab48f37b8fc09bee96f6cb9896257855e1af75b))

 -  update javadoc ([3006717053c6bb1](https://github.com/bsorrentino/langgraph4j/commit/3006717053c6bb1dfac8b1732793c23d4f5d31cd))



### ALM 

 -  bump to next intermediate version ([bb5dcce671d766f](https://github.com/bsorrentino/langgraph4j/commit/bb5dcce671d766f0952ac67a4caa7b3201a0559b))
   
 -  bump SNAPSHOT ([f20897527b8f644](https://github.com/bsorrentino/langgraph4j/commit/f20897527b8f644a73d10e1851b0deabcc6f5a73))
   

### Test 

 -  update PlantUMLgenerator tests ([9e2c92a8644f217](https://github.com/bsorrentino/langgraph4j/commit/9e2c92a8644f2178ff9f46ce02223a0ca4ba65b2))
   





<!-- "name: v1.0-20241111" is a release tag -->

## [v1.0-20241111](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20241111) (2024-11-11)

### Features

 *  **ToolNode**  Support for Tools specified dynamically ([d5d387baba0fa40](https://github.com/bsorrentino/langgraph4j/commit/d5d387baba0fa407ab117965c18f2df0de6fcef7))
     > resolve #44
   
 *  **AsyncNodeGenerator**  add 'resumedFromEmbed' state management in AsyncNodeGenerator ([d4a5dd2f65d1e3f](https://github.com/bsorrentino/langgraph4j/commit/d4a5dd2f65d1e3f5ee929b74b0e4c907429164a0))
     > work on #31
   
 *  **LLMStreamingGenerator**  make compliant with StreamingOutput ([d39eef962b417bd](https://github.com/bsorrentino/langgraph4j/commit/d39eef962b417bdd65c85d45857c9c7b248289cb))
     > work on #31
   
 *  **StreamingOutput**  add NodeOutput<> specialization to support LLM streaming ([aed9c23c09ed37d](https://github.com/bsorrentino/langgraph4j/commit/aed9c23c09ed37df535b25673776e4bdce1669e7))
     > work on #31
   
 *  **langchain4j**  bump async-generator version ([253cd7d5b1ed353](https://github.com/bsorrentino/langgraph4j/commit/253cd7d5b1ed353897a819b6220cbe4de854d038))
     > work on #31
   
 *  **langchain4j**  handle completion on LLMStreamingGenerator ([5740d1394aa35a6](https://github.com/bsorrentino/langgraph4j/commit/5740d1394aa35a6ddc83605078958924cfbb5ebb))
     > - provide an operator to convert response to Map&lt;&gt;
     > work on #31
   
 *  **CompiledGraph**  integrate embedding generator. ([e7938e49c137f12](https://github.com/bsorrentino/langgraph4j/commit/e7938e49c137f12671665213d19cd700947fe990))
     > - manage resume state after completion of an embed generator
     > work on #31
   
 *  **CompiledGraph**  add support of AsyncIterator.WithEmbed ([3c254e8e800a346](https://github.com/bsorrentino/langgraph4j/commit/3c254e8e800a346feeab63e1f5436ae1dc87c098))
     > work on #31
   
 *  make LLMStreamingGenerator an implementation of AsyncGenerator.WithResult ([c838f618ce9553c](https://github.com/bsorrentino/langgraph4j/commit/c838f618ce9553cc118f2919ab6c72e1dd298850))
     > work on #31
   
 *  add a LLMStreamingGenerator class ([d38eac5586b0ecd](https://github.com/bsorrentino/langgraph4j/commit/d38eac5586b0ecdfe1c190e099b07210c496faf6))
     > Convert the langchain4j StreamingResponseHandler to AsyncStream
     > work on #31
   
 *  add react demo notebook ([b19ab5e7456d4bc](https://github.com/bsorrentino/langgraph4j/commit/b19ab5e7456d4bc58fad6145c238400069cb06ea))
   


### Documentation

 -  refine serializer documentation ([74c9c33b7c5110c](https://github.com/bsorrentino/langgraph4j/commit/74c9c33b7c5110c984325838cbc6275435469bb8))
     > work on #38

 -  update changeme ([c33bddf902eac64](https://github.com/bsorrentino/langgraph4j/commit/c33bddf902eac64cd07851f925051a4723574a4e))



### ALM 

 -  bump intermediate version ([a43baab610d37ba](https://github.com/bsorrentino/langgraph4j/commit/a43baab610d37ba0e742a1102bd711f5da5caff0))
   
 -  bump to SNAPSHOT ([9d0a9a6be7b5825](https://github.com/bsorrentino/langgraph4j/commit/9d0a9a6be7b582588a06b594ebe9e88e5db558dc))
    > bump async-generator-jdk8 dep
 > work on #31

 -  bump to SNAPSHOT ([232eead50f7e2b9](https://github.com/bsorrentino/langgraph4j/commit/232eead50f7e2b9850af57112eb1e22c5cb2d94d))
   

### Test 

 -  **AgentExecutor**  complete test of LLM streaming on AgentExecutor sample ([d81a4a78336546d](https://github.com/bsorrentino/langgraph4j/commit/d81a4a78336546d802b0ffcbdb2cb90953be0af0))
    > resolve #31

 -  verify LLM streaming on AgentExecutor sample ([712e21ae4ab9802](https://github.com/bsorrentino/langgraph4j/commit/712e21ae4ab9802e544228b2f3fb8a00360eb35b))
    > work on #31

 -  add notebook to test LLMStreamingGenerator ([d51193d8b354ad7](https://github.com/bsorrentino/langgraph4j/commit/d51193d8b354ad734c3555af2366c585ab8f326a))
    > work on #31






<!-- "name: v1.0-20241029" is a release tag -->

## [v1.0-20241029](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20241029) (2024-10-29)

### Features

 *  **serializable**  remove ClassHolder,  StringHolder to avoid class loading problem ([a137abd7c5bea6f](https://github.com/bsorrentino/langgraph4j/commit/a137abd7c5bea6f90a1c19705d981270370160f6))
   
 *  add file system checkpoint saver ([5d8036d5ceabe99](https://github.com/bsorrentino/langgraph4j/commit/5d8036d5ceabe998458fbecb2449b9cbe01cae51))
     > work on #35
   
 *  **studio**  decouple the Studio implementation from Jetty Server ([98f070e6fa910b7](https://github.com/bsorrentino/langgraph4j/commit/98f070e6fa910b79c3e0c405a439d95d63f42fac))
     > resolve #42
   

### Bug Fixes

 -  **agentexecutor**  set jackson mapper visibility to ANY ([3cb700a3ed6da50](https://github.com/bsorrentino/langgraph4j/commit/3cb700a3ed6da50f64418b2b9068d1d13e5edce3))


### Documentation

 -  update changeme ([33d0b1e14367a4f](https://github.com/bsorrentino/langgraph4j/commit/33d0b1e14367a4ff9d2d6561181d815ed2bbab71))



### ALM 

 -  bump to new intermediate version ([be65332d27f2ad0](https://github.com/bsorrentino/langgraph4j/commit/be65332d27f2ad05d7e3422f28f35244c67b9507))
   
 -  bump to SNAPSHOT ([571d941c4537c66](https://github.com/bsorrentino/langgraph4j/commit/571d941c4537c661a554e76bd3f9baddddeaca19))
   
 -  **studio**  DTS refactoring ([ead9d9b3ca8f98e](https://github.com/bsorrentino/langgraph4j/commit/ead9d9b3ca8f98e52cbd51dc97cf40d0578f7b43))
    > work on #42

 -  **springai-agentexecutor**  skip site generation ([caa34b0b7b76bc7](https://github.com/bsorrentino/langgraph4j/commit/caa34b0b7b76bc78666d97c4d8ed3c79c6f04404))
   
 -  update actions ([39ac81de3847ea4](https://github.com/bsorrentino/langgraph4j/commit/39ac81de3847ea41af472d3de62a32a3a7b2d20d))
   

### Test 

 -  **studio**  DTS refactoring ([01a61fc4fc2e8bb](https://github.com/bsorrentino/langgraph4j/commit/01a61fc4fc2e8bb1453b2b0f96cf08ac62b1d0e8))
   





<!-- "name: v1.0-rc2" is a release tag -->

## [v1.0-rc2](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-rc2) (2024-10-25)


### Bug Fixes

 -  **pom**  add relative parent path ([3902dcb101d38c3](https://github.com/bsorrentino/langgraph4j/commit/3902dcb101d38c3496b76073f80964f8163e73d4))


### Documentation

 -  update docs with new version ([dcd765c0901b1e3](https://github.com/bsorrentino/langgraph4j/commit/dcd765c0901b1e36644ee5edd626d96b7e405c5c))

 -  update site pages ([5b8109c9d1af32d](https://github.com/bsorrentino/langgraph4j/commit/5b8109c9d1af32d54231da54d90e76042cd17d17))

 -  update README ([791b930e6de4ff6](https://github.com/bsorrentino/langgraph4j/commit/791b930e6de4ff68e60f94f2afe7cceb7e5b98f8))

 -  update changeme ([9f84f2d2f12eca4](https://github.com/bsorrentino/langgraph4j/commit/9f84f2d2f12eca41c83ed5bfb6c8fb95889e48c3))



### ALM 

 -  bump to new release candidate ([2fbb7e8c24f220f](https://github.com/bsorrentino/langgraph4j/commit/2fbb7e8c24f220f6ae961629ecc67e3920fc6e54))
   
 -  **action**  update deploy actions ([3d7153b2df8b46e](https://github.com/bsorrentino/langgraph4j/commit/3d7153b2df8b46e3c86a4d85d6f3d66716138af6))
    > select projects to deploy

 -  bump to SNAPSHOT version ([d7c167d5afca22f](https://github.com/bsorrentino/langgraph4j/commit/d7c167d5afca22fe587a689cc42113c775d96ff5))
   
 -  **image-to-diagram**  skip release ([8d54ba0778461f2](https://github.com/bsorrentino/langgraph4j/commit/8d54ba0778461f2e5f85bcad9ce3ef50f4d9ebd2))
   
 -  **image-to-diagram**  skip release ([5a9c47e4efa952b](https://github.com/bsorrentino/langgraph4j/commit/5a9c47e4efa952bf4720de1e6b443034e4384738))
   






<!-- "name: v1.0-20241024" is a release tag -->

## [v1.0-20241024](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20241024) (2024-10-24)

### Features

 *  add  sample using langgraph4j with springai ([069b1dae671419d](https://github.com/bsorrentino/langgraph4j/commit/069b1dae671419da124eea56bd1efd1f4e3a9954))
     > resolve #15
   
 *  **serializer**  change the standard serialization implementation to avoid class loading issues ([13fed3780bec648](https://github.com/bsorrentino/langgraph4j/commit/13fed3780bec648b8d419a315cd91f877bcbd0ce))
   


### Documentation

 -  remove BaseSerializer refs ([f0fc1a43417f03b](https://github.com/bsorrentino/langgraph4j/commit/f0fc1a43417f03b9c0f716c1ab88f6707ba149d6))

 -  update samples links ([90be73e67ed7189](https://github.com/bsorrentino/langgraph4j/commit/90be73e67ed7189a39d5fb6828b03c92acd799de))

 -  add serializer section ([a8d3f8e1e441fee](https://github.com/bsorrentino/langgraph4j/commit/a8d3f8e1e441fee2553f97dc0a952edc1f193d69))
     > work on #38

 -  update feature list ([ada695313e79123](https://github.com/bsorrentino/langgraph4j/commit/ada695313e791235ac3f9b44d47ce6a9172b8f4c))

 -  update project names ([e457098a54ccef4](https://github.com/bsorrentino/langgraph4j/commit/e457098a54ccef4a07a037816fc281d414d02239))

 -  update site docs ([7994f159cb9465e](https://github.com/bsorrentino/langgraph4j/commit/7994f159cb9465efc698ae609ea930221f69520f))

 -  update changeme ([8ab856f4743733f](https://github.com/bsorrentino/langgraph4j/commit/8ab856f4743733f0eff2027c1060be6526072150))


### Refactor

 -  update to project DTS ([9de853487ecc665](https://github.com/bsorrentino/langgraph4j/commit/9de853487ecc665eb0ad9469f120e1873dd7b64e))
    > added samples folder

 -  **agentexecutor**  update to be compliant with new serialization implementation ([6a408b2868e4fc7](https://github.com/bsorrentino/langgraph4j/commit/6a408b2868e4fc701eb643ec7cbbab8ad43b3c3c))
   
 -  **notebook**  update to be compliant with new serialization implementation ([cbba10a0f551a56](https://github.com/bsorrentino/langgraph4j/commit/cbba10a0f551a5652791d441f57cf44f9a46f58e))
   
 -  update to be compliant with new serialization implementation ([baf6fae5d9c6170](https://github.com/bsorrentino/langgraph4j/commit/baf6fae5d9c6170953fb7c5c95373af12de04f4a))
   

### ALM 

 -  bump to intermediate version ([c547d2035945345](https://github.com/bsorrentino/langgraph4j/commit/c547d2035945345b016b8bcec93873352810f190))
   
 -  move to next dev version ([9c2b09a81374734](https://github.com/bsorrentino/langgraph4j/commit/9c2b09a81374734fa9ba55f1e17c5404a97df80b))
   
 -  update deploy action ([e8e2c1dbd6cbf7e](https://github.com/bsorrentino/langgraph4j/commit/e8e2c1dbd6cbf7ee86c88e60153f3119b06d6fed))
    > remove JDK 8


### Test 

 -  **serializer**  update unit tests ([5f97207ecfe746a](https://github.com/bsorrentino/langgraph4j/commit/5f97207ecfe746ab5c7cf6a2e0e6d2d4fb47496e))
   





<!-- "name: v1.0-rc1" is a release tag -->

## [v1.0-rc1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-rc1) (2024-10-12)



### Documentation

 -  update readme ([b49b34cd912f406](https://github.com/bsorrentino/langgraph4j/commit/b49b34cd912f406dc74a379bdbf04e0cc9c3919d))

 -  update changeme ([07bb1763ba09206](https://github.com/bsorrentino/langgraph4j/commit/07bb1763ba09206ac35b57699b3e76044371f90c))



### ALM 

 -  bump to new version ([edf8bad65f537ec](https://github.com/bsorrentino/langgraph4j/commit/edf8bad65f537ecfd089e71a5bbaf8bfd94eab3a))
   






<!-- "name: v1.0-20241011" is a release tag -->

## [v1.0-20241011](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20241011) (2024-10-11)

### Features

 *  refine Serialization implementation ([199ae8d396d6e27](https://github.com/bsorrentino/langgraph4j/commit/199ae8d396d6e27e0087647b6eeeac5a78c441d1))
     > -  add StateSerializer abstract class that owns a StateFactory
     > -  refactor tests, samples and how-tos accordly
     > work on #29
   
 *  create branch on CompileGraph.updateState() ([0f1ecdb1cca8d5a](https://github.com/bsorrentino/langgraph4j/commit/0f1ecdb1cca8d5ab2a09b39316932c5e2e86f1e4))
   
 *  **serialization**  make serialization implementation supporting more serialization strategies ([23af74bf35514c9](https://github.com/bsorrentino/langgraph4j/commit/23af74bf35514c9f56fd9afdcc162f1d2c73fa33))
     > resolve #29
   

### Bug Fixes

 -  **serializer**  propagate ObjectOutputMapper and ObjectInputMapper ([1693b43314936db](https://github.com/bsorrentino/langgraph4j/commit/1693b43314936dbe8eacbf1ba599ad5407819376))


### Documentation

 -  overall site refinements ([4de05122e142e93](https://github.com/bsorrentino/langgraph4j/commit/4de05122e142e930e14ffd95d0a2df71014efd32))

 -  rename server-jetty to studio-jetty ([00f0d49b4872442](https://github.com/bsorrentino/langgraph4j/commit/00f0d49b48724420b6a72c171b98c756967df548))

 -  rename server-jetty to studio-jetty ([e0d5c7c646c5a8a](https://github.com/bsorrentino/langgraph4j/commit/e0d5c7c646c5a8a993f795cfb3abf22d9f15a0ac))

 -  rename server-jetty to studio-jetty ([d7cc59285a027c8](https://github.com/bsorrentino/langgraph4j/commit/d7cc59285a027c8a8764fb19846b7f072edf774b))

 -  update changeme ([fd5194ceb6ee8ff](https://github.com/bsorrentino/langgraph4j/commit/fd5194ceb6ee8ff9dd9181e7b986b5c660b579c7))


### Refactor

 -  Renamed LangGraphStreamingServer to LangGraphStreamingServerJetty ([022695c2dab9a7d](https://github.com/bsorrentino/langgraph4j/commit/022695c2dab9a7d0b7cc3a15b75be3b65e2815c2))
   
 -  update how-tos with new serializer ([72d0e33b24d83b9](https://github.com/bsorrentino/langgraph4j/commit/72d0e33b24d83b9f652ac898f60fd451ee153d29))
    > work on #29

 -  set public scope ([9cb9d84c59d0bc9](https://github.com/bsorrentino/langgraph4j/commit/9cb9d84c59d0bc97ff30aca2f45292e4b2cddf7e))
   
 -  **action**  deploy aggregate site to pages ([852e95c080ed056](https://github.com/bsorrentino/langgraph4j/commit/852e95c080ed056f5c9319e4c09a06ffb384a893))
    > work on #36

 -  remove usage of lombok.var ([bb2c73cb5b2c423](https://github.com/bsorrentino/langgraph4j/commit/bb2c73cb5b2c423ee33185c27b4358485e857102))
    > work on #36

 -  **deploy**  rename server-jetty to studio-jetty ([31594fca57ea79a](https://github.com/bsorrentino/langgraph4j/commit/31594fca57ea79a6b20fe558dcb7bd9be30bb31b))
   

### ALM 

 -  bump to new intermediate version ([68f92ed62519e79](https://github.com/bsorrentino/langgraph4j/commit/68f92ed62519e79602fba1d7acb2ce0ef3aaef1c))
   
 -  setup maven site aggegation ([c928339ddb546c8](https://github.com/bsorrentino/langgraph4j/commit/c928339ddb546c8e62c9b7c98c07f8ae678e5aca))
    > resolve #36

 -  bump to next developer version ([80ff8fdae63cbee](https://github.com/bsorrentino/langgraph4j/commit/80ff8fdae63cbee19ec997915ef9ec8ec5ed3ede))
   






<!-- "name: v1.0-20241006" is a release tag -->

## [v1.0-20241006](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20241006) (2024-10-06)

### Features

 *  **frondend**  add support for resume action ([1a4125dc08d1e41](https://github.com/bsorrentino/langgraph4j/commit/1a4125dc08d1e41c99c7467315dd439d101b341c))
     > solve #34
   
 *  **server**  add support for JSON deserialization ([817efeaeff620cc](https://github.com/bsorrentino/langgraph4j/commit/817efeaeff620cc07a05e3cc290384d69bba75e7))
     > work on #34
   
 *  **ToolNode**  add logging support ([5a982f426207881](https://github.com/bsorrentino/langgraph4j/commit/5a982f426207881ccf51148078a72f596ce1df6a))
   
 *  improve streammode management ([fcd5d6790208aca](https://github.com/bsorrentino/langgraph4j/commit/fcd5d6790208aca538b1a7da17c771a21fc87325))
     > work on #34
   
 *  **agentexecutor**  add JSON serialization support ([16d01791530f046](https://github.com/bsorrentino/langgraph4j/commit/16d01791530f0468e28ade34a1e8d28488fe62b6))
     > work on #34
   
 *  **agentexecutor**  add JSON serialization support ([3fa5ec0207f9315](https://github.com/bsorrentino/langgraph4j/commit/3fa5ec0207f9315277fbf2bcc4315c5f82bc0e29))
     > work on #34
   
 *  move agent-executor to jdk17 modules ([a7ca4d7aabb4c0c](https://github.com/bsorrentino/langgraph4j/commit/a7ca4d7aabb4c0c6a69c4cff152cde105cb5989b))
   
 *  **server**  add resume management ([bf030a74a9863f4](https://github.com/bsorrentino/langgraph4j/commit/bf030a74a9863f4526f09c4cf487f9a61a112077))
     > work on #34
   
 *  add agentexecutor notebook ([1465519e3309fb1](https://github.com/bsorrentino/langgraph4j/commit/1465519e3309fb1bf58d67ae76565d2ab31e1346))
   
 *  **frontend**  add support for state update ([4cfa9c1e9d3bfee](https://github.com/bsorrentino/langgraph4j/commit/4cfa9c1e9d3bfee5c207ee9f531114b33ca24ac4))
     > work on #34
   
 *  **frontend**  add support for checkpoint ([1c86f6852e6ab64](https://github.com/bsorrentino/langgraph4j/commit/1c86f6852e6ab645c52e7f8643a8852e10bb0cf2))
     > work #34
   
 *  **frontend**  parametrize 'collapsed' in NodeOutput ([78e453ee918b361](https://github.com/bsorrentino/langgraph4j/commit/78e453ee918b36180daa51200516137b9135d73d))
     > work on #34
   
 *  **frontend**  add support for state editing ([ccbe25383b0ef74](https://github.com/bsorrentino/langgraph4j/commit/ccbe25383b0ef74fa142b57b233bd562d8cc2e64))
     > add react component @microlink/react-json-view
     > work on #34
   
 *  allow customize Serializer using CompileConfig ([af0d3d65d51d047](https://github.com/bsorrentino/langgraph4j/commit/af0d3d65d51d0473a7c368be748968eb965955a8))
   

### Bug Fixes

 -  **frontend**  remove method duplication ([6768dcaca7b2a43](https://github.com/bsorrentino/langgraph4j/commit/6768dcaca7b2a438ea82193db6e3808b300ca7c9))
     > work on #24

 -  update scripts ([bae7e1a82bb9f32](https://github.com/bsorrentino/langgraph4j/commit/bae7e1a82bb9f32b505d203abeed874e9fc363b6))


### Documentation

 -  update readme ([d7dd7e77e2e6e83](https://github.com/bsorrentino/langgraph4j/commit/d7dd7e77e2e6e8358a1626cf24eac232dbcf4b31))

 -  add readme ([2456df4a7c24835](https://github.com/bsorrentino/langgraph4j/commit/2456df4a7c2483545f658f8373a42520f6932f58))

 -  add readme ([19dc1a5a3469b90](https://github.com/bsorrentino/langgraph4j/commit/19dc1a5a3469b90cd642b304fbdbb3c022280ef5))

 -  update comments ([b55692d98b1da2a](https://github.com/bsorrentino/langgraph4j/commit/b55692d98b1da2ab76501f1614bbef58bf5bac0d))


### Refactor

 -  rebrand from server to studio ([cd14b6e0f4d8052](https://github.com/bsorrentino/langgraph4j/commit/cd14b6e0f4d80527fb1823e5098b05ce3d554a0b))
   
 -  **agentexecutor**  rename package ([b158f62bc1ee716](https://github.com/bsorrentino/langgraph4j/commit/b158f62bc1ee71629ca3cb169a23e8357f0adaaa))
   
 -  rename MapSerialize to MapSerializer ([a60a78bcb600e46](https://github.com/bsorrentino/langgraph4j/commit/a60a78bcb600e465037926e2981f53176b26cb6a))
   
 -  **agentexecutor**  rename package ([5a2a50dec339fad](https://github.com/bsorrentino/langgraph4j/commit/5a2a50dec339fada133cce4fce5f45f320581705))
   
 -  **agentexecutor**  rename package ([0edb34814d505ab](https://github.com/bsorrentino/langgraph4j/commit/0edb34814d505aba273294112ec1f1c414859464))
   
 -  **server**  remove nodes from initialization ([1cf01acd23da255](https://github.com/bsorrentino/langgraph4j/commit/1cf01acd23da255fb922c1d4180ca214d9261e5e))
    > work on #34


### ALM 

 -  bump new intermediate version ([a2bf300573a929e](https://github.com/bsorrentino/langgraph4j/commit/a2bf300573a929edd7124cb932087dd8fc4c46f5))
   
 -  update frontend dist ([c6fc457ad3749fd](https://github.com/bsorrentino/langgraph4j/commit/c6fc457ad3749fd013cb8909120cd84bac151196))
    > work on #34

 -  update frontend dist ([ab51fbe2eae4243](https://github.com/bsorrentino/langgraph4j/commit/ab51fbe2eae424392da839a2c6c0cfb23e8b5577))
    > work on #34

 -  update frontend dist ([348c1421e9207b0](https://github.com/bsorrentino/langgraph4j/commit/348c1421e9207b01b5b5f5a6c7b796bedc81f820))
    > work on #34

 -  update frontend dist ([e02ea9bfb4a5b03](https://github.com/bsorrentino/langgraph4j/commit/e02ea9bfb4a5b03aef8577c08e3ebe5cb44a4b21))
    > work on #34

 -  update frontend dist ([ba1060a379d0a6a](https://github.com/bsorrentino/langgraph4j/commit/ba1060a379d0a6acbaaa54f6f28a60695423cbca))
    > work on #34

 -  update frontend dist ([d1a49576c8009ff](https://github.com/bsorrentino/langgraph4j/commit/d1a49576c8009ff99b8c176a5b29c7e2f1ab7f58))
    > work on #34

 -  bump to SNAPSHOT ([4b2e260aea75814](https://github.com/bsorrentino/langgraph4j/commit/4b2e260aea75814fb8442f7004d18e8740f07570))
   

### Test 

 -  add generation of json schema ([9cd4003e282bab5](https://github.com/bsorrentino/langgraph4j/commit/9cd4003e282bab5d73693a3d7639e515fc8611e1))
   





<!-- "name: v1.0-20240926" is a release tag -->

## [v1.0-20240926](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240926) (2024-09-26)

### Features

 *  improve logging ([ec148d763587456](https://github.com/bsorrentino/langgraph4j/commit/ec148d76358745683711b496f7e55df2f1724c83))
   
 *  add module with langchain4j integration utility ([769887576ec20f0](https://github.com/bsorrentino/langgraph4j/commit/769887576ec20f028907e730c8e71d0a6ddda9ce))
     > - Add Message Serializers
     > - Add ToolExecutionRequestSerializer
     > - Add ToolNode utility
     > resolve #26
   


### Documentation

 -  update readme ([19c3ad0253ff1d4](https://github.com/bsorrentino/langgraph4j/commit/19c3ad0253ff1d45e428dd7dac9169d4b9858cfa))


### Refactor

 -  **serializer**  move from static to instance methods ([4870289cc21587c](https://github.com/bsorrentino/langgraph4j/commit/4870289cc21587c493386c34d41ed2b9297c8b4a))
   
 -  **how-ts**  use of new langchain4j integration module ([ed2163bf7c62789](https://github.com/bsorrentino/langgraph4j/commit/ed2163bf7c6278960846ead490e13a0bed32c334))
    > work on #26

 -  **AgentExecutor**  use onf new langchain4j integration module ([369ef5d06277ba9](https://github.com/bsorrentino/langgraph4j/commit/369ef5d06277ba98ab2400d5ef7c9afd5182c565))
    > work on #26

 -  **adaptiverag**  update system prompt ([6ec6ab67f85ef14](https://github.com/bsorrentino/langgraph4j/commit/6ec6ab67f85ef14be4852325fba68567dc009f5c))
    > work on #32

 -  update adaptiverag notebook ([40f78ac94728ebf](https://github.com/bsorrentino/langgraph4j/commit/40f78ac94728ebfc8cfd66e0e65f879a1f515ea8))
   

### ALM 

 -  bump to intermediate version ([94a2de96aa7c3ca](https://github.com/bsorrentino/langgraph4j/commit/94a2de96aa7c3ca9624330e5a3b8ea8124153ddb))
   
 -  bump to new version of langchain4j 0.35.0 ([cee29a8b0a9e137](https://github.com/bsorrentino/langgraph4j/commit/cee29a8b0a9e137d3dc927ead21bfde1e378c448))
   
 -  bump to SNAPSHOT ([3225dd47184425f](https://github.com/bsorrentino/langgraph4j/commit/3225dd47184425f37fa38ce7b1542514a9179c58))
   

### Test 

 -  add new test ([508d8912a450ffa](https://github.com/bsorrentino/langgraph4j/commit/508d8912a450ffa5117bac85c8bc6aeda7158f44))
    > work on #32






<!-- "name: v1.0-20240924" is a release tag -->

## [v1.0-20240924](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240924) (2024-09-24)


### Bug Fixes

 -  **AnswerGrader**  update prompt pattern ([af72f94b8710f1c](https://github.com/bsorrentino/langgraph4j/commit/af72f94b8710f1c3e7359b94993a97c67c9b7337))
     > solve #32


### Documentation

 -  update readme ([cb806a45c87a0ed](https://github.com/bsorrentino/langgraph4j/commit/cb806a45c87a0edad3a7393a44fb0adfc88eaa86))

 -  update readme ([3cc326f6034cd29](https://github.com/bsorrentino/langgraph4j/commit/3cc326f6034cd29cef792bc92084ba383c5a6dda))

 -  update changeme ([8fcaabafda3ebf3](https://github.com/bsorrentino/langgraph4j/commit/8fcaabafda3ebf3d428bb1ed6c428b3d8f25d784))



### ALM 

 -  bump to SNAPSHOT version ([e009e33b2149ab6](https://github.com/bsorrentino/langgraph4j/commit/e009e33b2149ab6a747565aa4d9b45cca088ac69))
   


### Continuous Integration

 -  bump to develop version ([b547c0563bd9658](https://github.com/bsorrentino/langgraph4j/commit/b547c0563bd9658c1f43b1ba3fb9b5f665159194))
   




<!-- "name: v1.0-beta5" is a release tag -->

## [v1.0-beta5](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-beta5) (2024-09-24)

### Features

 *  **Server**  finalize thread support ([3f4ee84236c1aa6](https://github.com/bsorrentino/langgraph4j/commit/3f4ee84236c1aa6a3838e2730c62560774231507))
     > - NodeOutput Json Serialization
     > - read thread from get parameter
     > - add thread on straming result
     > resolve #24
   
 *  **backend**  add threads into init servlet ([1f0f20afe80db2c](https://github.com/bsorrentino/langgraph4j/commit/1f0f20afe80db2ce1338988bcfa1773d0cc470d9))
     > work on #24
   
 *  set initial thread ([525286a37f767a4](https://github.com/bsorrentino/langgraph4j/commit/525286a37f767a4134bf8d764a4e552ed3ac1bfd))
     > work on #24
   
 *  **frontend**  thread management refinement ([24042d4b65b505a](https://github.com/bsorrentino/langgraph4j/commit/24042d4b65b505ae5703e6e593400d27b9a5d9cd))
     > work on #24
   
 *  **CompiledGraph**  add streamSnapshots() method ([11fc73be5e7a89f](https://github.com/bsorrentino/langgraph4j/commit/11fc73be5e7a89f1b00212aa7414a6332e7fce98))
     > work on #24
   
 *  allow stream return subclass of NodeOutput ([780b2b90bf56af2](https://github.com/bsorrentino/langgraph4j/commit/780b2b90bf56af2156d518bbba715824bd6912d1))
     > allow return of StateSnapshot
     > work on #24
   
 *  **frontend**  add support for thread(tab) switch ([39b651d67ac0f7a](https://github.com/bsorrentino/langgraph4j/commit/39b651d67ac0f7a4242c04b53034f963ec3dcdd9))
     > work on #24
   
 *  **AsyncNodeGenerator**  add output factory method ([0f612363c68f9e6](https://github.com/bsorrentino/langgraph4j/commit/0f612363c68f9e61ea85ace04c83ed85bf138766))
     > work on #24
   
 *  **CompliedGraph**  update AsyncGenerator implementation ([04bcd136803086a](https://github.com/bsorrentino/langgraph4j/commit/04bcd136803086aad7b9a859be14c3f00fa3eac1))
     > remove AsyncGeneratorQueue
     > add a custom AsyncNodeGenerator
     > update unit test
   
 *  **RunnableConfig**  add StreamMode enum ([40fad25c6b2e327](https://github.com/bsorrentino/langgraph4j/commit/40fad25c6b2e32721ed91fdedd3d019e88fdbfe9))
     > work on #24
   
 *  add toString() ([7445a88be87845f](https://github.com/bsorrentino/langgraph4j/commit/7445a88be87845f6974bb9462cb2394da6e94189))
   
 *  **server**  threads Implementation refinements ([3e291dbc62b98c0](https://github.com/bsorrentino/langgraph4j/commit/3e291dbc62b98c081d3a82ea1c01f8cf722578cf))
     > start preparing backend and fronend to manage threads
     > work on #24
   
 *  move main implementation of getGraph() on StateGraph ([39da1f4c07db473](https://github.com/bsorrentino/langgraph4j/commit/39da1f4c07db47352331b7d2ada873196561a6fd))
     > work on #24
   
 *  move main implementation of getGraph() on StateGraph ([9c1b39b2f5fd5ab](https://github.com/bsorrentino/langgraph4j/commit/9c1b39b2f5fd5ab752ba4be0736f09d674dac87f))
     > work on #24
   
 *  **collection**  add last() and lastMinus() support for the List<T> ([52bfbec8084adfb](https://github.com/bsorrentino/langgraph4j/commit/52bfbec8084adfbadab505e5f08b28adf941a637))
     > work on #24
   
 *  **serializer**  add support for mimetype ([7ca1a6169376932](https://github.com/bsorrentino/langgraph4j/commit/7ca1a6169376932ab95abe557d09f95eb9190fa3))
     > work on #24
   
 *  **frontend**  add thread button ([75f975e9bea7ce8](https://github.com/bsorrentino/langgraph4j/commit/75f975e9bea7ce84af58387218f55121794e00e7))
     > work on #24
   

### Bug Fixes

 -  separate thread panel ([4383975f68972b7](https://github.com/bsorrentino/langgraph4j/commit/4383975f68972b768e6f421a9e79bb34c1a49676))
     > work on #24

 -  diagram generator issue with START on mermaid ([7474b86718cbf43](https://github.com/bsorrentino/langgraph4j/commit/7474b86718cbf43d87def78e4beee88b60ea0392))
     > work on #24

 -  mermaid generation ([53b68e0ffef4291](https://github.com/bsorrentino/langgraph4j/commit/53b68e0ffef4291792e13e70d975d3ac662a0aaf))
     > START, END issue
     > work on #24

 -  graph  layout ([ab0a0c2cf91b5be](https://github.com/bsorrentino/langgraph4j/commit/ab0a0c2cf91b5be6959d0c0d40fbeb984838660f))
     > sync container and grapsh svg size
     > work on #24


### Documentation

 -  update readme ([cb01f90346068d6](https://github.com/bsorrentino/langgraph4j/commit/cb01f90346068d667ff6ac2446542f2643b0ed11))

 -  **pom**  add executions comment ([0193aefd115351f](https://github.com/bsorrentino/langgraph4j/commit/0193aefd115351fe0979682b79d87d47fce669df))

 -  update changeme ([e8552308ed95488](https://github.com/bsorrentino/langgraph4j/commit/e8552308ed95488229f765dc23fe0dd3f50c3185))

 -  update changeme ([95b8c4541900bab](https://github.com/bsorrentino/langgraph4j/commit/95b8c4541900bab6b6429506369f5ae5ba0600af))


### Refactor

 -  update model ([1cac3901446ebf5](https://github.com/bsorrentino/langgraph4j/commit/1cac3901446ebf542328a4f8c2946f8f47c2c262))
    > work on #24

 -  **pom**  add hint for server exection ([0e8d7eca3dfdc2e](https://github.com/bsorrentino/langgraph4j/commit/0e8d7eca3dfdc2e8c3efc917a037c6d848d16553))
    > work on #24

 -  **executor.js**  rename property ([bef2f436746b3df](https://github.com/bsorrentino/langgraph4j/commit/bef2f436746b3df4031047e11a7fce884a714536))
    > work on #24

 -  **NodeOutput**  remove lombok @Value and remove final class constraint ([41a095ebffff6b5](https://github.com/bsorrentino/langgraph4j/commit/41a095ebffff6b525151d16a57f1161a10dc3a69))
    > work on #24

 -  make StateSnapshot inherit from NodeOutput ([b210f381e061aa6](https://github.com/bsorrentino/langgraph4j/commit/b210f381e061aa63527bf7285f15493c09c3c29e))
    > work on #24

 -  **server**  enable use of StateGraph ([16aefea482a7aaa](https://github.com/bsorrentino/langgraph4j/commit/16aefea482a7aaa2590232b2160b769566205a77))
    > work on #24

 -  **samples**  expose StateGraph intead of CompiledGraph ([f193da8f1d3f632](https://github.com/bsorrentino/langgraph4j/commit/f193da8f1d3f6326a9011b0c047c29a7877d5513))
    > work on #24

 -  refine set-version script ([26351252b389547](https://github.com/bsorrentino/langgraph4j/commit/26351252b389547d4810b28a74444f13718ab62a))
   

### ALM 

 -  bump to new version ([abdb35503b611a7](https://github.com/bsorrentino/langgraph4j/commit/abdb35503b611a750d12d9ec0ded547cba298942))
   
 -  **forntend**  update dist ([644e7a1c232ec48](https://github.com/bsorrentino/langgraph4j/commit/644e7a1c232ec488c3455f65392384d71a6573b4))
   
 -  update webapp dist ([bebf5c045bd939d](https://github.com/bsorrentino/langgraph4j/commit/bebf5c045bd939d56cdd2b7fd40e3f848b789979))
    > work on #24

 -  bump to SNAPSHOT ([a3eb4e92badd416](https://github.com/bsorrentino/langgraph4j/commit/a3eb4e92badd4163182bf82215f6a00fd362f139))
   
 -  **server**  update frontend dist ([fe4aff4ca05cc04](https://github.com/bsorrentino/langgraph4j/commit/fe4aff4ca05cc04a4c8dd946516428377fd65be9))
   
 -  bump to SNAPSHOT ([ee26478dab44f09](https://github.com/bsorrentino/langgraph4j/commit/ee26478dab44f09da09365f8d1ac41fa9d4172ac))
   
 -  bump deps versions ([963be9b8e824f80](https://github.com/bsorrentino/langgraph4j/commit/963be9b8e824f8066ea1a6629c16f33d390d675c))
    > lit, tailwindcss, postcss, autoprefixer, typescript
 > work on #24

 -  update css gen tools ([8b1b0e2b0224e93](https://github.com/bsorrentino/langgraph4j/commit/8b1b0e2b0224e931233308cad49d6d3204a87c13))
   






<!-- "name: v1.0-20240915" is a release tag -->

## [v1.0-20240915](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240915) (2024-09-15)





### ALM 

 -  bump new intermediate version ([683a8ab92e407da](https://github.com/bsorrentino/langgraph4j/commit/683a8ab92e407da2f2234d68516a176433bb34b6))
   






<!-- "name: v1.0-20240907" is a release tag -->

## [v1.0-20240907](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240907) (2024-09-07)

### Features

 *  **howtos**  visualize plantuml image ([3ade1774a4e76b0](https://github.com/bsorrentino/langgraph4j/commit/3ade1774a4e76b04c3f4c4237d9e132ec006c948))
   

### Bug Fixes

 -  **diagram**  update diagram generation ([786cede1ac528b6](https://github.com/bsorrentino/langgraph4j/commit/786cede1ac528b60ad2724a08a7e0d2e2bfe1772))
     > - PlantUML
     > - Mermaid


### Documentation

 -  **CompiledGraph**  javadoc refinements ([9d10c4806e7ff21](https://github.com/bsorrentino/langgraph4j/commit/9d10c4806e7ff2144528e259629b55e5987b1c36))

 -  update changeme ([c709e3800d24ebf](https://github.com/bsorrentino/langgraph4j/commit/c709e3800d24ebf23e9985748556caf7751a18b3))



### ALM 

 -  bump developer version ([b87196100219609](https://github.com/bsorrentino/langgraph4j/commit/b871961002196091a8fbbc56a34ebe24dd37f8b5))
   
 -  add set-version shell ([d8c034974444d65](https://github.com/bsorrentino/langgraph4j/commit/d8c034974444d65215c0ab91d289d92395c99fa8))
   

### Test 

 -  **notebook**  test notebook from @hide212131 ([fe4ea4c2a93bc0e](https://github.com/bsorrentino/langgraph4j/commit/fe4ea4c2a93bc0ef1f28042c9552fb0abee40166))
   





<!-- "name: v1.0-20240906" is a release tag -->

## [v1.0-20240906](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240906) (2024-09-06)

### Features

 *  **how-tos**  add loggin support ([27c7afd0ecc609b](https://github.com/bsorrentino/langgraph4j/commit/27c7afd0ecc609bca4dfd18ea214928f9271c0f4))
   
 *  **CompiledGraph**  optimize code ([4e9a763700b9814](https://github.com/bsorrentino/langgraph4j/commit/4e9a763700b9814d7ac444151533c6b3486f521b))
     > minimize cloneState() calls
   


### Documentation

 -  update changeme ([890367f24ce3d27](https://github.com/bsorrentino/langgraph4j/commit/890367f24ce3d27c910a2a6fde8deee79bad2c81))


### Refactor

 -  merge PR #23 ([9e1248b468f1204](https://github.com/bsorrentino/langgraph4j/commit/9e1248b468f1204ebbc83b75c35fac798bb57fec))
   

### ALM 

 -  update git ignore ([1983a7380200aa2](https://github.com/bsorrentino/langgraph4j/commit/1983a7380200aa2ad282a2fef3d5eb090bc78dd7))
   
 -  bump to developer release ([e8a2f07f273fe59](https://github.com/bsorrentino/langgraph4j/commit/e8a2f07f273fe5903a9a39525991e062968732a1))
   






<!-- "name: v1.0-beta4" is a release tag -->

## [v1.0-beta4](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-beta4) (2024-09-06)

### Features

 *  **notebook**  add "How to view and update past graph state" ([ae60f3094661e4e](https://github.com/bsorrentino/langgraph4j/commit/ae60f3094661e4e2bfa5d9000bb11ec532d51bd4))
   

### Bug Fixes

 -  pause management ([9ec77fb711d11c3](https://github.com/bsorrentino/langgraph4j/commit/9ec77fb711d11c336e8ad26d3002f9e7b6868960))
     > - check resume startpoint
     > - refine state cloning
     > - improve unit test
     > work on #14


### Documentation

 -  update readme ([67b61a7be81bd11](https://github.com/bsorrentino/langgraph4j/commit/67b61a7be81bd113fce703a8e48f429722b34ffc))

 -  **time-travel.ipynb**  update ([a1216b97240b6b9](https://github.com/bsorrentino/langgraph4j/commit/a1216b97240b6b9f34bb188aae54b10ae5fe964d))

 -  update maven site ([c89323fe75721c6](https://github.com/bsorrentino/langgraph4j/commit/c89323fe75721c68d81ece1e860b8255dda77fef))

 -  update site ([79fb38b1da3ba77](https://github.com/bsorrentino/langgraph4j/commit/79fb38b1da3ba775e33e8436a5fee9499de0e2bb))

 -  update changeme ([a6c54e814b4b9ef](https://github.com/bsorrentino/langgraph4j/commit/a6c54e814b4b9ef747eb13f37f6b1e7f27730cb4))

 -  add "How to view and update past graph state" ([40dd70e59c6d283](https://github.com/bsorrentino/langgraph4j/commit/40dd70e59c6d2833a2b12094d02d4ebf82de120e))

 -  update changeme ([8adee9a867c7de7](https://github.com/bsorrentino/langgraph4j/commit/8adee9a867c7de79c25824ffd9ce1b8acd61164e))


### Refactor

 -  update git ignore ([a09be23ce723385](https://github.com/bsorrentino/langgraph4j/commit/a09be23ce72338550dac754809bdabb3215020b3))
   
 -  **TryConsumer**  add logging ([7023501ea4ea944](https://github.com/bsorrentino/langgraph4j/commit/7023501ea4ea944e99c9d4878a93de1dc2b8674e))
   

### ALM 

 -  bump new beta version ([25f45aa52fc1f38](https://github.com/bsorrentino/langgraph4j/commit/25f45aa52fc1f3817954e7e2626d88d6ac400c99))
   
 -  utility shells refinements ([f1765769556e024](https://github.com/bsorrentino/langgraph4j/commit/f1765769556e0242a96e1abbe6371e423cb8731b))
   
 -  bump langchai4j version ([b0689ac981193b6](https://github.com/bsorrentino/langgraph4j/commit/b0689ac981193b6cb42432410180f9cb1a55fd6f))
   






<!-- "name: v1.0-20240905" is a release tag -->

## [v1.0-20240905](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240905) (2024-09-05)

### Features

 *  **notebook**  add "How to view and update past graph state" ([8df2d1101fa898e](https://github.com/bsorrentino/langgraph4j/commit/8df2d1101fa898ea828da0db3791fce6a11ff890))
   

### Bug Fixes

 -  pause management ([7042bce11521c91](https://github.com/bsorrentino/langgraph4j/commit/7042bce11521c9145e6c923da24497a74f245fe6))
     > - check resume startpoint
     > - refine state cloning
     > - improve unit test


### Documentation

 -  add "How to view and update past graph state" ([99d9130895e0b0a](https://github.com/bsorrentino/langgraph4j/commit/99d9130895e0b0aa62b71565511a7eef5c3461db))

 -  update changeme ([609cc0f20f03f88](https://github.com/bsorrentino/langgraph4j/commit/609cc0f20f03f88d45ba9e481aff27126d78c669))


### Refactor

 -  **TryConsumer**  add logging ([7c752de608c863e](https://github.com/bsorrentino/langgraph4j/commit/7c752de608c863e0ab26928923dac01e4d2c273c))
   
 -  update git ignore ([3eaabbeef0c25be](https://github.com/bsorrentino/langgraph4j/commit/3eaabbeef0c25be559cc208d13b1bcbc69d31796))
   
 -  **serializer**  remove CheckpointSerializer ([26128e081863ea2](https://github.com/bsorrentino/langgraph4j/commit/26128e081863ea259403c0e39c5b06f787ce4c77))
   
 -  **serializer**  remove type() method ([ebbac63139e2e9f](https://github.com/bsorrentino/langgraph4j/commit/ebbac63139e2e9fa2834787564a22dd7c1ad25e9))
    > Simplify implementation


### ALM 

 -  move to next dev release ([e8b0735d16687bf](https://github.com/bsorrentino/langgraph4j/commit/e8b0735d16687bfe7922dbcea2095e90c42dc8d9))
   






<!-- "name: v1.0-beta3" is a release tag -->

## [v1.0-beta3](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-beta3) (2024-09-03)

### Features

 *  **serilaizer**  enhance the serialization api ([06ed83d7c8ba6c8](https://github.com/bsorrentino/langgraph4j/commit/06ed83d7c8ba6c8360e60f0cada7a3a124a230c5))
     > - add BaseSerializer
     > - add Custom Serializer support
   
 *  **notebook**  add persistence how-to using a java notebook ([b89dbcfe05123b8](https://github.com/bsorrentino/langgraph4j/commit/b89dbcfe05123b8459c26a95d663608177ed91d3))
   
 *  **persistence.ipynb**  add AiMessage Serializer ([363600c0b629d42](https://github.com/bsorrentino/langgraph4j/commit/363600c0b629d423bc70dd9e577a99290b3b417b))
   
 *  **serializer**  add custom serializer support ([f958f0c03e5b9c8](https://github.com/bsorrentino/langgraph4j/commit/f958f0c03e5b9c816188aa927b82cc3a7ae754a5))
   
 *  **persistence.ipynb**  add workflow execution ([c4bf0819b3f65a2](https://github.com/bsorrentino/langgraph4j/commit/c4bf0819b3f65a2c94c4bc2e715b251bf0aedc1a))
   
 *  **persistence**  refine Graph definition ([f09aeb6a0b583b4](https://github.com/bsorrentino/langgraph4j/commit/f09aeb6a0b583b4c1c620978772371a331e79cce))
   
 *  add support of java notebook based on 'rapaio-jupyter-kernel' ([41ab46694adf90e](https://github.com/bsorrentino/langgraph4j/commit/41ab46694adf90e9853d5e53f4acd0f89fa650ff))
   
 *  add factory methods to create custom Channel ([8abb17312f788fb](https://github.com/bsorrentino/langgraph4j/commit/8abb17312f788fb2b510145e22d35f31c2a96c01))
   

### Bug Fixes

 -  **CompiledGraph**  when asNode is provided next node is evaluated keeping in consideration edges ([d3595cb41ec9f64](https://github.com/bsorrentino/langgraph4j/commit/d3595cb41ec9f64ce9e2f2806e564a2a94ef67c3))
     > work on #14


### Documentation

 -  **site**  update documentation ([254f64f7bfb41a1](https://github.com/bsorrentino/langgraph4j/commit/254f64f7bfb41a14da82aba7ea4e4af67ef42c11))
     > - persistence howto

 -  update links ([0d7b1df3766efe4](https://github.com/bsorrentino/langgraph4j/commit/0d7b1df3766efe4d7901ce029e6fc77f597e06f6))
     > work on #12

 -  update links ([a5af831f480b016](https://github.com/bsorrentino/langgraph4j/commit/a5af831f480b016540e489e71126efb6818cc3b0))
     > work on #12

 -  update links ([2a1f1b8ca779832](https://github.com/bsorrentino/langgraph4j/commit/2a1f1b8ca779832ff7f0329b3763840fc8373c7d))
     > work on #12

 -  update links ([45eb6850811a164](https://github.com/bsorrentino/langgraph4j/commit/45eb6850811a164ffa6518448c76dbf082569024))

 -  update links ([7245d150fed8c1d](https://github.com/bsorrentino/langgraph4j/commit/7245d150fed8c1df07a3e14e65c9a3b9f1257979))

 -  update links ([6c77da53ac2fdc7](https://github.com/bsorrentino/langgraph4j/commit/6c77da53ac2fdc7a8867f90ca71a85a30e1fa8fb))

 -  update links ([4449fb4148d5a90](https://github.com/bsorrentino/langgraph4j/commit/4449fb4148d5a907a2d45319362928b5c1dbb9a7))

 -  update ([b82abebd61df183](https://github.com/bsorrentino/langgraph4j/commit/b82abebd61df183930c8ea288d4a31c61a974bcd))

 -  update ([ea080158b19a91d](https://github.com/bsorrentino/langgraph4j/commit/ea080158b19a91dbb0b56e6563895e7006e9123a))

 -  add javadoc task on site  generation ([c236773dbef7752](https://github.com/bsorrentino/langgraph4j/commit/c236773dbef77520d1471b6c785725e75017ac30))
     > work on #12

 -  add Conceptual Guides ([33ba7527b9d2c79](https://github.com/bsorrentino/langgraph4j/commit/33ba7527b9d2c79f9154f3afa6fdce8289f8cd30))
     > work on #12

 -  update readme ([1a087fca04acfc6](https://github.com/bsorrentino/langgraph4j/commit/1a087fca04acfc65740befdbedd97250794fd884))

 -  update changeme ([04f1ff7a00254f3](https://github.com/bsorrentino/langgraph4j/commit/04f1ff7a00254f31520653cc07ebec84247ed9b6))

 -  update changeme ([d8bdbcb7309dde5](https://github.com/bsorrentino/langgraph4j/commit/d8bdbcb7309dde548bce27aa97ed0fec02f65287))

 -  update changeme ([160d7093b6123ec](https://github.com/bsorrentino/langgraph4j/commit/160d7093b6123ec645bcbe63e4830d70c6c6b297))


### Refactor

 -  **TryConsumer**  add log ([526f296c70bc587](https://github.com/bsorrentino/langgraph4j/commit/526f296c70bc5872c4dfb58f4ea27146350a0794))
   
 -  remove unused import ([eeb00a08078449c](https://github.com/bsorrentino/langgraph4j/commit/eeb00a08078449cf9e6036ac940bf2ac59e69783))
   
 -  add @@FunctionalInterface annotation ([e37621cf6bdbef0](https://github.com/bsorrentino/langgraph4j/commit/e37621cf6bdbef0782c420be75bd8790d516e930))
   

### ALM 

 -  bump version 1.0-beta3 ([d7fbde9e3a6f8d5](https://github.com/bsorrentino/langgraph4j/commit/d7fbde9e3a6f8d5d2f549b153cd54a30f5c15eea))
   






<!-- "name: v1.0-20240828" is a release tag -->

## [v1.0-20240828](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240828) (2024-08-28)

### Features

 *  add support for 'interruptBeforereak' and 'interruptAfter' ([3083d9d95d05393](https://github.com/bsorrentino/langgraph4j/commit/3083d9d95d05393ac651579431b39935a597df94))
   
 *  **MemorySaver**  add rw lock to memory saver ([a00054b2169e348](https://github.com/bsorrentino/langgraph4j/commit/a00054b2169e348b0db4f5c5702d05e224a57a35))
   
 *  **CompiledGraph**  expose state Management ([183a0ceb7069f1f](https://github.com/bsorrentino/langgraph4j/commit/183a0ceb7069f1f96b2e69590843c9ed08f7818b))
     > - getState
     > - getStateHistory
     > - updateState
     > - update streaming processing to support graph resume
     > work on #14
   
 *  **StateSnapshot**  add support StateSnaphot object ([cc86564cc2f2759](https://github.com/bsorrentino/langgraph4j/commit/cc86564cc2f27595559018635e6af48117b28275))
     > work on #14
   
 *  **RunnableConfig**  add checkpointId and nextNode properties ([509d7ebd50eef7a](https://github.com/bsorrentino/langgraph4j/commit/509d7ebd50eef7aff6ba74d4865645a738de44a1))
     > work on #14 #20
   
 *  **Checkpoint**  store the nextNodeId ([6e1ca60de4572af](https://github.com/bsorrentino/langgraph4j/commit/6e1ca60de4572afa7b28f00daf5fec3d78d6c55d))
     > work on #14
   
 *  **BaseCheckpointSaver**  add support for ThreadId ([05c293faa56cfad](https://github.com/bsorrentino/langgraph4j/commit/05c293faa56cfad8c3f94bb8f69bc74731f65e25))
     > work on #20
   
 *  add MapSerializer ([1407b41f8d412eb](https://github.com/bsorrentino/langgraph4j/commit/1407b41f8d412ebb7337764f764be6365ee12521))
   


### Documentation

 -  update changeme ([2a47f9f0edca129](https://github.com/bsorrentino/langgraph4j/commit/2a47f9f0edca1293683159758e386932b9c109c9))

 -  update changeme ([5e3259ec27d6aca](https://github.com/bsorrentino/langgraph4j/commit/5e3259ec27d6aca32af698d47b11a58ffce12119))

 -  update changeme ([8d965e7b07df3fd](https://github.com/bsorrentino/langgraph4j/commit/8d965e7b07df3fd14312e86e08adf33015844ce6))


### Refactor

 -  rename getCheckpointSaver() to checkpointSaver() because it returns an Optional ([fd072d239a0e7f8](https://github.com/bsorrentino/langgraph4j/commit/fd072d239a0e7f8a4d5e5068b87eef31b4fedce7))
   
 -  make TryConsumer public ([2447a2380ffcc40](https://github.com/bsorrentino/langgraph4j/commit/2447a2380ffcc40a02141fe471d8c7ad738b6329))
   
 -  make TryConsumer public ([3f92e4c7c3b8243](https://github.com/bsorrentino/langgraph4j/commit/3f92e4c7c3b824339f9720068a037189db1d6287))
   
 -  **CompiledGraph**  refine state management ([c2a8e876dbc0342](https://github.com/bsorrentino/langgraph4j/commit/c2a8e876dbc0342c37331b097e68b54d5f9f6fe2))
    > work on #14

 -  **StateSnapshot**  delegate next to checkpoint ([8199a0d7a039192](https://github.com/bsorrentino/langgraph4j/commit/8199a0d7a039192daeaeee4bbdf2149967c20913))
    > work on #14

 -  **Checkpoint**  move from AgentState to Map<K,V> ([fb742ac09bdf0db](https://github.com/bsorrentino/langgraph4j/commit/fb742ac09bdf0db170305ca3e2202b766a2e8823))
   
 -  **AgentState**  add updateState utility methods ([af0bd50092583e2](https://github.com/bsorrentino/langgraph4j/commit/af0bd50092583e2fa8da987b935fb4a929ca7e78))
   
 -  rename InvokeConfig to RunnableConfig ([40a910f32dd882c](https://github.com/bsorrentino/langgraph4j/commit/40a910f32dd882c7c7168cc326311861a6ef58e6))
    > work on #20

 -  rename InvokeConfig to RunnableConfig ([fe9ff015241ef0d](https://github.com/bsorrentino/langgraph4j/commit/fe9ff015241ef0dc88e10e36b605be01fcab589b))
    > work on #20


### ALM 

 -  move to next developer version ([5a7fa556b33613c](https://github.com/bsorrentino/langgraph4j/commit/5a7fa556b33613cd2e1d7c3287baa4e0da2d1f78))
   
 -  add changelog update shell ([cc44dfea071d6f7](https://github.com/bsorrentino/langgraph4j/commit/cc44dfea071d6f7333ebe68d32a41142a8b2a2b5))
   

### Test 

 -  updateState tests ([0ed422a1bee5410](https://github.com/bsorrentino/langgraph4j/commit/0ed422a1bee54102207c50ca535bfd54704ddd70))
   
 -  add unit test for threadId support ([3dc0089ff93a40f](https://github.com/bsorrentino/langgraph4j/commit/3dc0089ff93a40f799d9ac1364617f6b40f20611))
    > resolve #20






<!-- "name: v1.0-beta2" is a release tag -->

## [v1.0-beta2](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-beta2) (2024-08-10)



### Documentation

 -  update readme ([6fe3cfab3b90028](https://github.com/bsorrentino/langgraph4j/commit/6fe3cfab3b90028de59435599035600a2ef79a02))

 -  update readme ([784b8af1883114f](https://github.com/bsorrentino/langgraph4j/commit/784b8af1883114f1c0c7946e69c223f06591b501))

 -  update changelog ([b505197e4c4b39e](https://github.com/bsorrentino/langgraph4j/commit/b505197e4c4b39ee21b3fcb3cd5e10cd5186534f))


### Refactor

 -  rename AgentExecutor.builder to AgentExecutor.graphBuilder ([7f2416657a7cff8](https://github.com/bsorrentino/langgraph4j/commit/7f2416657a7cff8272775b80320b798f030d034a))
   

### ALM 

 -  move to next version ([ed9fa6c3fe69e81](https://github.com/bsorrentino/langgraph4j/commit/ed9fa6c3fe69e810ace34d431055030ec43e7554))
   






<!-- "name: v1.0-20240809" is a release tag -->

## [v1.0-20240809](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240809) (2024-08-09)

### Features

 *  add utitlity for support serialization of nullable value ([7a820294f37b9e1](https://github.com/bsorrentino/langgraph4j/commit/7a820294f37b9e1d361f2210295806fde5c59293))
   

### Bug Fixes

 -  update builder visibility to public ([bffa8a46fc7c228](https://github.com/bsorrentino/langgraph4j/commit/bffa8a46fc7c22884da02291947d9d8705b781a7))


### Documentation

 -  update changelog ([ad9d96752715baa](https://github.com/bsorrentino/langgraph4j/commit/ad9d96752715baac7b894b8c0816288f2a4f6124))




### Test 

 -  verify checkpoint on agentexecutor ([9776cbd69e5ca2f](https://github.com/bsorrentino/langgraph4j/commit/9776cbd69e5ca2f776fec3b62f67efb104c0591d))
   





<!-- "name: v1.0-20240807-1" is a release tag -->

## [v1.0-20240807-1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240807-1) (2024-08-07)

### Features

 *  refine collection utilities ([1be0f7279663fb4](https://github.com/bsorrentino/langgraph4j/commit/1be0f7279663fb46b9d07eaf3546c7d31829db5c))
   
 *  enable fluent interface on graph definition ([787d41c3537821f](https://github.com/bsorrentino/langgraph4j/commit/787d41c3537821f731151359ada376b72832eba3))
     > deprecate: setEntryPoint, setFinishPoint, setConditionalEntryPoint
   
 *  add Channel support ([cd500132ef8b133](https://github.com/bsorrentino/langgraph4j/commit/cd500132ef8b13369823d16c75b7a775ffc176a8))
     > add reducer, default value provider
     > add AppenderChannel to manage accumulated list of values
     > deprecate AppendableValue
     > work on #13
   


### Documentation

 -  update readme ([475ffaba3ce93f1](https://github.com/bsorrentino/langgraph4j/commit/475ffaba3ce93f1e6f3b72bfc5e158028ace1287))


### Refactor

 -  use graph fluent interface ([b6ee47b842bde7c](https://github.com/bsorrentino/langgraph4j/commit/b6ee47b842bde7c7da33d5c7b178c686f14ddc6b))
   



### Continuous Integration

 -  rename deploy snapshot action ([7d1b273178c505d](https://github.com/bsorrentino/langgraph4j/commit/7d1b273178c505d975aa4343cf4bafc5e0d9eeae))
   
 -  rename deploy snapshot action ([06a2820cd43dfcc](https://github.com/bsorrentino/langgraph4j/commit/06a2820cd43dfccdbae4a8cd7695af98d9b4db17))
   
 -  rename deploy snapshot action ([59e44c91aae44bb](https://github.com/bsorrentino/langgraph4j/commit/59e44c91aae44bb950ee7da07643667e8e7be16f))
   
 -  refinement of deploy snapshot action ([4063d2b1c1654a1](https://github.com/bsorrentino/langgraph4j/commit/4063d2b1c1654a11333bd19e691d345606dc6838))
   




<!-- "name: v1.0-20240807" is a release tag -->

## [v1.0-20240807](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240807) (2024-08-07)

### Features

 *  finalize Checkpoint implementation ([77e4723753a79b1](https://github.com/bsorrentino/langgraph4j/commit/77e4723753a79b1cb8d6a0e7b05c9da97216e93b))
     > resolve  #11
   
 *  finalize checkpoint implementation ([1564efca643c31c](https://github.com/bsorrentino/langgraph4j/commit/1564efca643c31c48df649d7729b646b3648f40f))
     > add AgentState and Checkpointer serializer
     > add support for MemorySaver
     > work on #11
   
 *  start implementing checkpoint ([f9800ec98253f50](https://github.com/bsorrentino/langgraph4j/commit/f9800ec98253f5015775ff088b5486ce4ea3c4d7))
     > - add BaseCheckpointSaver
     > - add Checkpoint
     > - add CheckpointConfig
     > - add CompileConfig
     > - add InvokeConfig
     > work on #11
   


### Documentation

 -  update readme ([9ed434ad02926ba](https://github.com/bsorrentino/langgraph4j/commit/9ed434ad02926ba253008f745fd648ec724e7e64))

 -  add description, scm, license ([3d2b2a9a260a6d4](https://github.com/bsorrentino/langgraph4j/commit/3d2b2a9a260a6d455621d48f65982fc56e5589b1))
     > work on #4


### Refactor

 -  make AppendableValueRW serializable ([a49decf1386fedd](https://github.com/bsorrentino/langgraph4j/commit/a49decf1386fedd1bf28ec417f19ee53847ee939))
    > work on #11


### ALM 

 -  move to next developer release ([5db6022ca330927](https://github.com/bsorrentino/langgraph4j/commit/5db6022ca33092780e0072075d144a70d73316e0))
   
 -  upgrade async-generator dependency, add slf4j to test scope ([b8ab321093899c6](https://github.com/bsorrentino/langgraph4j/commit/b8ab321093899c6e7293430d79bfc5ab94012736))
    > work on #11



### Continuous Integration

 -  add deploy snapshot action ([93074fbd0e0beef](https://github.com/bsorrentino/langgraph4j/commit/93074fbd0e0beef4d84284d290910ebc2e499415))
   
 -  add release profile ([47cb279dbba1a00](https://github.com/bsorrentino/langgraph4j/commit/47cb279dbba1a00d03ec3639ddfc3a0f8968d25d))
    > work on #4

 -  update deploy.yaml ([e5ef005e09f7b76](https://github.com/bsorrentino/langgraph4j/commit/e5ef005e09f7b762a9b766323005eda8442d93a3))
    > work on #4

 -  update deploy script ([1862707da26c5a9](https://github.com/bsorrentino/langgraph4j/commit/1862707da26c5a99506059146298ad4956a74739))
    > add sonatype token
 > work on #4

 -  update deploy script ([0585f8caeb80027](https://github.com/bsorrentino/langgraph4j/commit/0585f8caeb800272ff9a6735863a57aabf6b6418))
    > remove release profile
 > work on #4





<!-- "name: v1.0-beta1" is a release tag -->

## [v1.0-beta1](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-beta1) (2024-08-02)



### Documentation

 -  update readme ([c96574d32dd2395](https://github.com/bsorrentino/langgraph4j/commit/c96574d32dd2395ad4afe861a4b013e1f96b9573))

 -  update readme ([feeb46eb2b3f9e9](https://github.com/bsorrentino/langgraph4j/commit/feeb46eb2b3f9e9750f5a182c6d30b64c0a142f4))

 -  update changelog template ([c66fc6b1774cc90](https://github.com/bsorrentino/langgraph4j/commit/c66fc6b1774cc90e617668ae57bca1a0de02a80d))

 -  update readme ([13afc2265e2d4a2](https://github.com/bsorrentino/langgraph4j/commit/13afc2265e2d4a2409c1bde1bfc4f7f9f6f9899c))

 -  update changelog ([df07e2d4137abcb](https://github.com/bsorrentino/langgraph4j/commit/df07e2d4137abcb99656d579a1a669a1d845f6cc))


### Refactor

 -  rename core module from langgraph4j-jdk8 to langgraph4j--core-jdk8 ([ccf6282e9ab9d5e](https://github.com/bsorrentino/langgraph4j/commit/ccf6282e9ab9d5eba48adc8b56d307b9f4103b5e))
   

### ALM 

 -  update version to next release ([6c4d365ded24b5a](https://github.com/bsorrentino/langgraph4j/commit/6c4d365ded24b5ad83aace0bdfab848a0fe2887e))
   


### Continuous Integration

 -  add maven plugin for deployment ([3a195394e5b3379](https://github.com/bsorrentino/langgraph4j/commit/3a195394e5b33792b7340590ef6bd6195c1fb6ce))
    > working on #4

 -  add github action for deployment ([ab8db1d51e28c7e](https://github.com/bsorrentino/langgraph4j/commit/ab8db1d51e28c7ef99cb7867406fc4a6dccc4be1))
   




<!-- "name: v1.0-20240729" is a release tag -->

## [v1.0-20240729](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240729) (2024-07-29)


### Bug Fixes

 -  **lg4j-graph**  svg height settings ([f4ae09f6fea0025](https://github.com/bsorrentino/langgraph4j/commit/f4ae09f6fea0025b8f9f3d21bb549f71ca08a1a2))

 -  remive resize handler on disconnection ([5b76da01f475aeb](https://github.com/bsorrentino/langgraph4j/commit/5b76da01f475aeb84a515d9637adfa4bfec6454f))


### Documentation

 -  update readme. refine changelog ([6e1a6864ef9b29a](https://github.com/bsorrentino/langgraph4j/commit/6e1a6864ef9b29a62b314d8a846af2c1b3c122f3))

 -  update changelog ([ab5fbc2666f13b3](https://github.com/bsorrentino/langgraph4j/commit/ab5fbc2666f13b37a0b068dcafd625414510bf5c))


### Refactor

 -  **web-app**  fix new distribution ([b1a377ebc65b7df](https://github.com/bsorrentino/langgraph4j/commit/b1a377ebc65b7df552ea5a292aa151719c014832))
   
 -  upgrade to langchain4j 0.33.0 ([afaf3274b20b523](https://github.com/bsorrentino/langgraph4j/commit/afaf3274b20b5235fa504cee8d3f9de95c570abc))
   
 -  **server-jetty**  load logging.properties from fs not from classpath anymore ([cd4f30737d3197a](https://github.com/bsorrentino/langgraph4j/commit/cd4f30737d3197a3d7f2eeb8366a61ebf48f5203))
   

### ALM 

 -  update distribution ([7082a1fbb7692db](https://github.com/bsorrentino/langgraph4j/commit/7082a1fbb7692db8b3095d46c5410dbe60312034))
   






<!-- "name: v1.0-20240723" is a release tag -->

## [v1.0-20240723](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240723) (2024-07-23)

### Features

 *  **frontend**  add zoom support on graph view ([c6d7fab152c1edb](https://github.com/bsorrentino/langgraph4j/commit/c6d7fab152c1edbe8c39f098c612d4c0c267f0b3))
     > - switch to vanilla webcomponent t betetr control mermaid rendering
     > - add d3 zoom support
     > - remember zoom trasformation between rendering
   
 *  experiment using d3 zoom on svg ([44be1a1f52f6d20](https://github.com/bsorrentino/langgraph4j/commit/44be1a1f52f6d20df4fd47fbedfdc2e72aa9910f))
   
 *  **server-jetty**  set dark theme by default ([c4a06ec88e12332](https://github.com/bsorrentino/langgraph4j/commit/c4a06ec88e12332a08dc3cf2bd1c8f78ed8f0dd1))
   
 *  **server-jetty**  add adaptiveRAG test ([050c628f45e369a](https://github.com/bsorrentino/langgraph4j/commit/050c628f45e369a7290cb0561370474ab3b5729c))
   

### Bug Fixes

 -  **core**  generation graph ([df75b6db12a659f](https://github.com/bsorrentino/langgraph4j/commit/df75b6db12a659f4d7a93bb618bd877675bdd20a))
     > check printConditionalEdge on declareConditionalStart()


### Documentation

 -  update changelog ([dd7be4e71dd91a1](https://github.com/bsorrentino/langgraph4j/commit/dd7be4e71dd91a1e94c95a3522b3cb55e82f2305))

 -  update changelog ([a21c7a72a5f39f5](https://github.com/bsorrentino/langgraph4j/commit/a21c7a72a5f39f5cce8f36602457c50152e9f737))


### Refactor

 -  **frontend**  clean code ([d792b96b1c4a733](https://github.com/bsorrentino/langgraph4j/commit/d792b96b1c4a733c616fd95591f450e31756a4b3))
   
 -  **frontend**  : clean code ([36ec62756424f38](https://github.com/bsorrentino/langgraph4j/commit/36ec62756424f386e2439cbf97f33132377f4b54))
   
 -  **fornt-end**  lg4j-graph fills the parent size ([796b09d5f61349e](https://github.com/bsorrentino/langgraph4j/commit/796b09d5f61349e1776187674644fc7c449eb10e))
   

### ALM 

 -  **server**  update dist ([b640ce23393f190](https://github.com/bsorrentino/langgraph4j/commit/b640ce23393f19058a6fd08aa0b648b13aa86c03))
   
 -  **server-jetty**  update dist ([6577e93f7cd9520](https://github.com/bsorrentino/langgraph4j/commit/6577e93f7cd9520c181a92af02336f00624e9a9b))
   

### Test 

 -  **fromend**  add adaptiverag flow as test ([cfe8486d4578731](https://github.com/bsorrentino/langgraph4j/commit/cfe8486d4578731749e4e76062ba3afdf66a9841))
   
 -  **server-jetty**  adaptiverag test refinements ([53c15da4b3a8b44](https://github.com/bsorrentino/langgraph4j/commit/53c15da4b3a8b44401daa334ef4ca9e4716b1a3a))
   





<!-- "name: v1.0-20240719" is a release tag -->

## [v1.0-20240719](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240719) (2024-07-19)

### Features

 *  toggle conditional-edge representation ([4e55eda05e23bf3](https://github.com/bsorrentino/langgraph4j/commit/4e55eda05e23bf3053f907833afde4c7f09a7e04))
   
 *  **front-end**  make result panel scrollable ([fbd73f12d10b77a](https://github.com/bsorrentino/langgraph4j/commit/fbd73f12d10b77a8dc6070619b25605bb21126ea))
   
 *  **server-jetty**  add completion of async context ([d67ef3f6d98de23](https://github.com/bsorrentino/langgraph4j/commit/d67ef3f6d98de2378efa0316997c9e6490bbf3cb))
   
 *  add @alenaksu/json-viewer component ([2cc3a69c2448965](https://github.com/bsorrentino/langgraph4j/commit/2cc3a69c2448965e01567f8f93aed4277b4ba7ea))
     > work on #9
   
 *  add support for custom mapper ([b0fe566790be739](https://github.com/bsorrentino/langgraph4j/commit/b0fe566790be7391afe5086d0ccb61e02b54fa06))
     > work on #9
   
 *  add agent executor sample ([d7ddb58e61e34d3](https://github.com/bsorrentino/langgraph4j/commit/d7ddb58e61e34d339bd488e842ac6adbf10ce469))
     > work on #9
   
 *  add support for custom title ([48ec649edf97477](https://github.com/bsorrentino/langgraph4j/commit/48ec649edf97477def6475fc989ab1b280070f97))
     > work on #9
   
 *  stream returns also 'start' and 'stop' steps ([bb6e0de5ccb8ca8](https://github.com/bsorrentino/langgraph4j/commit/bb6e0de5ccb8ca8e8305c102de9ff605be96bf12))
     > work on #9
   
 *  generate mermaid with node id ([7967a93439a6590](https://github.com/bsorrentino/langgraph4j/commit/7967a93439a6590546cb86c2883e839d1f804ee3))
     > need for node hightlight
     > work on #9
   
 *  finalize node highlight ([cd934894f2b8c8a](https://github.com/bsorrentino/langgraph4j/commit/cd934894f2b8c8a219db9ba34fd70d4426384c4d))
     > work on #9
   
 *  highlight active node ([feae491063ac3a5](https://github.com/bsorrentino/langgraph4j/commit/feae491063ac3a5f738328dc78e223b50cd78230))
     > work on #9
   
 *  move from war to jar packaging ([e942aefdbf96dc1](https://github.com/bsorrentino/langgraph4j/commit/e942aefdbf96dc155acc465ae384c1ff7f291e98))
     > better for embedding
     > work on #9
   
 *  back-end refinements ([bdec3a3e9828fe7](https://github.com/bsorrentino/langgraph4j/commit/bdec3a3e9828fe75b7a8ee9b50c6db8a52a9c492))
     > - log support
     > - return nodeoutput json representation
     > - update front-end distribution
     > work on #9
   
 *  front-end refinements ([f48618cdee8f095](https://github.com/bsorrentino/langgraph4j/commit/f48618cdee8f095c77714070b2eb67983395aab9))
     > - UI/UX refinements
     > - build input form from metadata
     > - improve result visualization
     > work on #9
   
 *  **server**  add builder ([9e8109d84887a3a](https://github.com/bsorrentino/langgraph4j/commit/9e8109d84887a3af535ba6dc9abb650e939b246b))
     > with support of:
     > - port
     > - inputArg metadata
     > work on #9
   
 *  **js**  finalize front-end candidate release ([33becfcec58795d](https://github.com/bsorrentino/langgraph4j/commit/33becfcec58795d750abe5b40ca62253f653510c))
     > work on #9
   
 *  **LangGraphStreamingServer**  implementation refinement ([3b8c6cf83100e2f](https://github.com/bsorrentino/langgraph4j/commit/3b8c6cf83100e2ff890f9b74f3461576f513e05a))
     > work on #9
   
 *  **jetty**  upgrade frontend dist ([3cf8b643e76e094](https://github.com/bsorrentino/langgraph4j/commit/3cf8b643e76e0945e4c6f2031be5af58720390be))
     > work on #9
   
 *  **core**  move on development version of async-iterator ([4d385b9bf9b739d](https://github.com/bsorrentino/langgraph4j/commit/4d385b9bf9b739dbb793a0eed4d28b63216043c5))
     > work on #9
   
 *  **LangGraphStreamingServer**  complete pilot implementation ([5ebfa769c20ed35](https://github.com/bsorrentino/langgraph4j/commit/5ebfa769c20ed35d4d09718906e03056afe08148))
     > work on #9
   
 *  update front-end dist ([0b3fc281afeb3b1](https://github.com/bsorrentino/langgraph4j/commit/0b3fc281afeb3b13e12c1f675af0abfd579edf58))
     > work on #9
   
 *  webapp frontend refinements ([920bae03c20315b](https://github.com/bsorrentino/langgraph4j/commit/920bae03c20315bea807b6bef271ed48d2f1ee42))
     > work on #9
   
 *  setup lit + tailwind project ([4ddc639a5dac0e0](https://github.com/bsorrentino/langgraph4j/commit/4ddc639a5dac0e0e26b10a43bc64b8aec74b21a7))
     > work on #9
   
 *  add http streaming support ([d57e9170056480c](https://github.com/bsorrentino/langgraph4j/commit/d57e9170056480ca71267957325cf469b09fbdc3))
   

### Bug Fixes

 -  mermaid loading diagram error ([5ccef4548baa66f](https://github.com/bsorrentino/langgraph4j/commit/5ccef4548baa66fd04d4acfce04e52d5801dea74))
     > avoid use of (deprecated) mermaidAPI


### Documentation

 -  update readme ([c45b04983271663](https://github.com/bsorrentino/langgraph4j/commit/c45b04983271663b688419ed569b263d4340c8b7))

 -  update readme ([30820697591c000](https://github.com/bsorrentino/langgraph4j/commit/30820697591c0001e6d7758faf103d9b009bd1f7))

 -  update readme ([bec7e46926765e2](https://github.com/bsorrentino/langgraph4j/commit/bec7e46926765e24779ec594efd48c5339ef1232))

 -  update changelog ([43966c93dfcd6c5](https://github.com/bsorrentino/langgraph4j/commit/43966c93dfcd6c5013307af13fbab91e8bcc3762))


### Refactor

 -  playground refinements ([1fd90b006dba0e4](https://github.com/bsorrentino/langgraph4j/commit/1fd90b006dba0e42ddcc216cd34fcf256c081c0b))
   
 -  rename server module ([88e6a7037fc6f52](https://github.com/bsorrentino/langgraph4j/commit/88e6a7037fc6f523ae6967164240dc80d4c765ea))
    > from &#x27;jetty&#x27; to &#x27;server-jetty&#x27;
 > resolve #9

 -  clean code ([eab97854fc8cd79](https://github.com/bsorrentino/langgraph4j/commit/eab97854fc8cd791c43e157fbb6adce3b5be0b1f))
    > work on #9

 -  clean code ([0e9bc7660f1522e](https://github.com/bsorrentino/langgraph4j/commit/0e9bc7660f1522e1ec3fa026cdb12947a882e0ad))
   
 -  add compile method ([7b795ff466e283d](https://github.com/bsorrentino/langgraph4j/commit/7b795ff466e283d8fc5930b758f638f95ad6ebdd))
    > - useful for streaming server impl
 > work on #9

 -  remove unused import ([9a339ce8bf52554](https://github.com/bsorrentino/langgraph4j/commit/9a339ce8bf52554bff7d4abeb06876732cf7b45d))
   
 -  update comment ([af609956e1b71c9](https://github.com/bsorrentino/langgraph4j/commit/af609956e1b71c9ee9b4513ae6af05ebc8065b99))
   

### ALM 

 -  **frontend**  update dist ([e96162e6752380c](https://github.com/bsorrentino/langgraph4j/commit/e96162e6752380c74b2ad157328c176c611d9ccc))
   
 -  **server**  update dist ([53dfb22ce7a73c1](https://github.com/bsorrentino/langgraph4j/commit/53dfb22ce7a73c10c07e629319a1693ce5a97103))
   
 -  update git ignore ([b79170a1028cc9d](https://github.com/bsorrentino/langgraph4j/commit/b79170a1028cc9dd297b75bd5a4839ead18d4553))
   
 -  update front-end dist ([22e943d435bf4c4](https://github.com/bsorrentino/langgraph4j/commit/22e943d435bf4c47321e84298d4fd9f3fec1bede))
    > work on #9

 -  update front-end dist ([153fc4f42e9bc80](https://github.com/bsorrentino/langgraph4j/commit/153fc4f42e9bc80ae934bb7684218ef9ba456787))
    > work on #9

 -  update fornt-end dist ([17e57501073be75](https://github.com/bsorrentino/langgraph4j/commit/17e57501073be75f9ec9d9f381b61a50200ab27b))
    > work on #9

 -  upgrade front-end dist ([38298373112466a](https://github.com/bsorrentino/langgraph4j/commit/38298373112466a28ba3d45f5cc545eae2205591))
    > work on #9

 -  upgrade java-async-generator lib ([4ba26ddddcf869f](https://github.com/bsorrentino/langgraph4j/commit/4ba26ddddcf869f3a370039f5662ffaf2c7d5435))
    > work on #9

 -  support of java8 and java17 building ([dc8ff48b8c1a233](https://github.com/bsorrentino/langgraph4j/commit/dc8ff48b8c1a2330ebf75dd81810be28df10bd9b))
    > work on #9


### Test 

 -  add agent executor dep ([fa6d4f313a2a646](https://github.com/bsorrentino/langgraph4j/commit/fa6d4f313a2a64669f7065c3ccbdc235bd83b6fd))
    > work on #9

 -  add agent executor sample refinements ([1ba559ab80943ff](https://github.com/bsorrentino/langgraph4j/commit/1ba559ab80943ff6d7d679f8778c2f723e6b29fd))
    > work on #9

 -  reset steps counter after ending graph ([acf32a7da40195b](https://github.com/bsorrentino/langgraph4j/commit/acf32a7da40195b4282b9f81c301f8707676c5e5))
    > work on #9

 -  **jetty**  graph test refinements ([16c873cfc91132d](https://github.com/bsorrentino/langgraph4j/commit/16c873cfc91132d1d0d919578e45beacdb16f0cb))
    > reduce number of iteration
 > work on #9

 -  test refinements ([880a7c7db56533e](https://github.com/bsorrentino/langgraph4j/commit/880a7c7db56533e47b149f3843ad9ccecb722158))
    > work on #9






<!-- "name: v1.0-20240621" is a release tag -->

## [v1.0-20240621](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240621) (2024-06-21)

### Features

 *  **core**  add support of Mermaid diagram-as-node syntax generation ([a0fd5a95a4d0493](https://github.com/bsorrentino/langgraph4j/commit/a0fd5a95a4d049355877cc8ac2a46a53d0a9d345))
     > resolve #5
   
 *  **core**  add support for contidional entrypoint in getGraph() method ([1a81fe399211a62](https://github.com/bsorrentino/langgraph4j/commit/1a81fe399211a62b8ccafedee67f7f56e330ba99))
   


### Documentation

 -  update readme ([ef7953b94c87190](https://github.com/bsorrentino/langgraph4j/commit/ef7953b94c8719023be2a29e214010c6e1bf7e93))

 -  **adaptive rag**  add mermaid diagram ([46d6fc535bf8de1](https://github.com/bsorrentino/langgraph4j/commit/46d6fc535bf8de1188ba862ad20acd9977ed0292))
     > resolve #5

 -  **adaptive-rag**  update readme ([47ec3b494836544](https://github.com/bsorrentino/langgraph4j/commit/47ec3b4948365440e3e84b54029a5e8e29a9a022))

 -  **adaptive-rag**  update readme ([5e11dd489628466](https://github.com/bsorrentino/langgraph4j/commit/5e11dd489628466fe997f2d60308a0c33d4c88f5))

 -  **adaptive-rag**  update readme ([c28381fb18938df](https://github.com/bsorrentino/langgraph4j/commit/c28381fb18938df71508aff88414375a8848e454))

 -  update changelog ([86996b108be06a7](https://github.com/bsorrentino/langgraph4j/commit/86996b108be06a7f543975bb348608de2c16282d))


### Refactor

 -  update project layout ([4cbd5c042052c32](https://github.com/bsorrentino/langgraph4j/commit/4cbd5c042052c32155600bcc7862745006b2a8a7))
   
 -  **adaptive-rag**  make opening Chroma Store lazy ([6892438d158e6dd](https://github.com/bsorrentino/langgraph4j/commit/6892438d158e6dd262a91271951534d15ca685ef))
    > resolve #5

 -  **core**  support of multiple diagram-as-node syntax generation ([9af787d3b85d03f](https://github.com/bsorrentino/langgraph4j/commit/9af787d3b85d03f6c2523e5b0d74736214403d1f))
    > make diagram as code generation through an abstract class
 > work on #5

 -  **adaptive-rag**  clean code ([53911383e137db7](https://github.com/bsorrentino/langgraph4j/commit/53911383e137db7fe74f718ed0ed0deb038acb70))
   

### ALM 

 -  **adaptive-rag**  add exec tasks ([985275c10292bfe](https://github.com/bsorrentino/langgraph4j/commit/985275c10292bfe7cd67fe632df58530aecc9f1c))
    > - upsert: Populate Chroma Vector store
 > - chroma: start Chroma Server
 > - app: start demo app


### Test 

 -  **adaptive-rag**  add getGraph() test ([6e79ee6b44e5d9b](https://github.com/bsorrentino/langgraph4j/commit/6e79ee6b44e5d9b5cf626a18350d45b3ec3667f3))
   





<!-- "name: v1.0-20240619" is a release tag -->

## [v1.0-20240619](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240619) (2024-06-19)

### Features

 *  update example ([dd746afb3534e99](https://github.com/bsorrentino/langgraph4j/commit/dd746afb3534e9987ddc013eddebf6ce2d2a812d))
     > work on #6
   
 *  add maven exec plugin to run example ([19b55d7dc37e9bd](https://github.com/bsorrentino/langgraph4j/commit/19b55d7dc37e9bd54235b67fe609555b7a6cc249))
     > work on #6
   
 *  complete AdaptiveRag implementation ([e3d62406268951f](https://github.com/bsorrentino/langgraph4j/commit/e3d62406268951f0c2617246e7fe54f501615d8f))
     > resolve #6
   
 *  complete nodes and edges ([7ab1205eb8a66f2](https://github.com/bsorrentino/langgraph4j/commit/7ab1205eb8a66f23e8fc1b94e2ee6f680ebfc299))
     > work on #6
   
 *  add webSearch method ([383476f5a230fd8](https://github.com/bsorrentino/langgraph4j/commit/383476f5a230fd8131f418570acf8a33dcfc069e))
     > work on #6
   
 *  start adaptive rag implementation ([16a0aefe2155ce0](https://github.com/bsorrentino/langgraph4j/commit/16a0aefe2155ce0863f71939f64b70585ef2bcd2))
     > work on #6
   
 *  add tavily dependency ([7af44a68b18bac7](https://github.com/bsorrentino/langgraph4j/commit/7af44a68b18bac7351161145964c66daed019b53))
     > work on #6
   
 *  add question rewriter function object ([ba4664a974fa9a5](https://github.com/bsorrentino/langgraph4j/commit/ba4664a974fa9a5b6a7af30c4763ad449919437c))
     > work on #6
   
 *  add retrieval grader function object ([36674feafc7ceb7](https://github.com/bsorrentino/langgraph4j/commit/36674feafc7ceb7e18de01f02f2da2615f400fe1))
     > work on #6
   
 *  add tavily integration ([a79e5e5434ae45b](https://github.com/bsorrentino/langgraph4j/commit/a79e5e5434ae45be53943643635773400359c68e))
     > work on #6
   
 *  **adaptiverag**  start implementin adaptive rag ([538c5d72644ee6f](https://github.com/bsorrentino/langgraph4j/commit/538c5d72644ee6f9a0429c7a60839a11cac370a7))
     > 1. create docker compose to host chroma
     > 2. create docker container to upsert sample data
     > 3. start implementation + unit test
     > work on #6
   

### Bug Fixes

 -  remove api key ref ([6753a9a63c5bad4](https://github.com/bsorrentino/langgraph4j/commit/6753a9a63c5bad42124c6e20a1d182c4a10f0f9a))


### Documentation

 -  update readme ([370a18d8d8e8121](https://github.com/bsorrentino/langgraph4j/commit/370a18d8d8e812148c304daf01e08d172e6be1c8))

 -  update readme ([21df2aa7a0555fc](https://github.com/bsorrentino/langgraph4j/commit/21df2aa7a0555fc97d14f5abe3d62e406df4b1b8))
     > work on #6

 -  update readme ([e34f8155c1c4919](https://github.com/bsorrentino/langgraph4j/commit/e34f8155c1c49197e8e48e9589bf8f1913c276f3))

 -  update readme ([4152b9dbec18429](https://github.com/bsorrentino/langgraph4j/commit/4152b9dbec18429ea8f7cc709eb1364d6db6ec04))

 -  **GraphRepresentation**  update javadoc ([7cd31cc54d367bb](https://github.com/bsorrentino/langgraph4j/commit/7cd31cc54d367bb7176b87b321c58a5515083d28))

 -  update readme ([7f2d137325df1a7](https://github.com/bsorrentino/langgraph4j/commit/7f2d137325df1a73fa3bc23e714dc896ec0026bf))

 -  add changelog ([b9491d73dcd64a7](https://github.com/bsorrentino/langgraph4j/commit/b9491d73dcd64a7d53824c2855757eac5de36c2e))


### Refactor

 -  remove deprecated object ([7ca950ac0bb95a5](https://github.com/bsorrentino/langgraph4j/commit/7ca950ac0bb95a5f40c58fc664a86eb3339bfb4e))
    > work on #6

 -  remove useless images ([854699636ee7b3a](https://github.com/bsorrentino/langgraph4j/commit/854699636ee7b3abfa68709fe9fb3bf312dcb32f))
    > work on #6


### ALM 

 -  add .env sample ([8f755d14e6ae8ef](https://github.com/bsorrentino/langgraph4j/commit/8f755d14e6ae8efe064b04be779f99d03fbf5f9b))
   
 -  remove useless files ([a9d5e4dbc360ba9](https://github.com/bsorrentino/langgraph4j/commit/a9d5e4dbc360ba979eda0c4b47b3178924881552))
   
 -  add adaptive-rag module ([3552f238e7f262a](https://github.com/bsorrentino/langgraph4j/commit/3552f238e7f262a96ff54466e059d7bb1dce6d6d))
   
 -  upgrade langchain4j version ([9a78ff59429e326](https://github.com/bsorrentino/langgraph4j/commit/9a78ff59429e326ff3205b33563c490d6180c36e))
   

### Test 

 -  chroma test ([4b4cc5338449377](https://github.com/bsorrentino/langgraph4j/commit/4b4cc5338449377976998ec2780641fcc9b97986))
    > work on #6

 -  update test ([b592b0d9436d6f0](https://github.com/bsorrentino/langgraph4j/commit/b592b0d9436d6f0377c40d37f3a4b665bd8f1b9b))
    > work on #6






<!-- "name: v1.0-20240610" is a release tag -->

## [v1.0-20240610](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240610) (2024-06-10)



### Documentation

 -  update javadoc ([62ef3598db2908f](https://github.com/bsorrentino/langgraph4j/commit/62ef3598db2908f6339d5b78cc73769ef8d1e5bd))

 -  update javadoc ([4fedaff7af27e4b](https://github.com/bsorrentino/langgraph4j/commit/4fedaff7af27e4b878c71afd5db9ae9124a732a1))

 -  **core-jdk8**  update project site ([20f03b989a343e2](https://github.com/bsorrentino/langgraph4j/commit/20f03b989a343e2375398798a7a450199f7d1c1b))

 -  update readme ([5e284f4d0263023](https://github.com/bsorrentino/langgraph4j/commit/5e284f4d02630232585854fc465ef6227f574d47))

 -  update readme ([3b435f05aa27df3](https://github.com/bsorrentino/langgraph4j/commit/3b435f05aa27df3c08fb8d7b41fdfef71d7e9682))

 -  update readme ([46a20691660be2f](https://github.com/bsorrentino/langgraph4j/commit/46a20691660be2f747276e1b69d5bbe9de67a44a))


### Refactor

 -  diagram code generation method ([6be2a8e7e2ced6a](https://github.com/bsorrentino/langgraph4j/commit/6be2a8e7e2ced6af76fc61d85dbf92ab83899490))
   



### Continuous Integration

 -  **deploy-pages**  set jdk8 ([fe67a69e0300784](https://github.com/bsorrentino/langgraph4j/commit/fe67a69e0300784b4403d683130d74d93d877ec0))
   
 -  setup deploy-pages action ([0c5c855e9c51761](https://github.com/bsorrentino/langgraph4j/commit/0c5c855e9c51761cb65282ff419ddd00323b46a9))
   




<!-- "name: v1.0-20240520" is a release tag -->

## [v1.0-20240520](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240520) (2024-05-20)

### Features

 *  refine PlantUML graph generation ([bd61ecb5cc4bfe7](https://github.com/bsorrentino/langgraph4j/commit/bd61ecb5cc4bfe717b270fbb55f9c8c644914879))
   
 *  generation of plantuml from graph definition ([7e8a739ce8581ec](https://github.com/bsorrentino/langgraph4j/commit/7e8a739ce8581ec40a1c7005425f1abe78a9b454))
   


### Documentation

 -  update readme ([847ace83f146e57](https://github.com/bsorrentino/langgraph4j/commit/847ace83f146e5756c8f5d0427aa0373efcaf5ac))

 -  update readme ([35daca70c65eae3](https://github.com/bsorrentino/langgraph4j/commit/35daca70c65eae3082effcb35d2e191c27c8b76c))

 -  update documantation ([e70f2ccc2682d84](https://github.com/bsorrentino/langgraph4j/commit/e70f2ccc2682d84bdff5b35ab7e808158bcfd4a5))


### Refactor

 -  clean code ([daac0e8e71eb56f](https://github.com/bsorrentino/langgraph4j/commit/daac0e8e71eb56f6c059ef25b547fa9cab50ab32))
   
 -  GraphState to StateGraph ([cfa7c92d65483ea](https://github.com/bsorrentino/langgraph4j/commit/cfa7c92d65483ea9a0970bd1ae7e7c3286c1e8e6))
    > make compliant to original LangGraph



### Test 

 -  refine generate graph tests ([393003c0017abaa](https://github.com/bsorrentino/langgraph4j/commit/393003c0017abaad556dbb7475c87bdd6faaab65))
   





<!-- "name: v1.0-20240516" is a release tag -->

## [v1.0-20240516](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240516) (2024-05-16)

### Features

 *  **iamge_to_diagram**  add sub-graph for error review ([149705364f0265b](https://github.com/bsorrentino/langgraph4j/commit/149705364f0265bd94f935a2b0f829214a3c878f))
   


### Documentation

 -  update readme ([876c68284f38521](https://github.com/bsorrentino/langgraph4j/commit/876c68284f38521b74b60e51b5e1f83fe4040191))




### Test 

 -  improve unit tests ([ef8a8e9c1454b6b](https://github.com/bsorrentino/langgraph4j/commit/ef8a8e9c1454b6bd50460631530b0037807c0caf))
   





<!-- "name: v1.0-20240514" is a release tag -->

## [v1.0-20240514](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240514) (2024-05-14)

### Features

 *  **agent-jdk8**  finalize image_to_diagram_with_correction graph implementation ([bc1ef69d21c7f98](https://github.com/bsorrentino/langgraph4j/commit/bc1ef69d21c7f98060fc077da861f2a1a62576b8))
   
 *  **agent-jdk8**  implementing ImageToDiagram auto correction process ([e6e89e63bd2156a](https://github.com/bsorrentino/langgraph4j/commit/e6e89e63bd2156abfb44452e7f49748c7940dec2))
     > 1. evaluate generated diagram
     > 2. catch errors
     > 3. submit errors to Agent Review
     > 4. Generate new diagram
     > 5. got to (1)
   
 *  add image to diagram use case ([0d7d09f1ba1e49b](https://github.com/bsorrentino/langgraph4j/commit/0d7d09f1ba1e49ba72afce75bdf47e5b4f5fc60f))
   


### Documentation

 -  update readme ([e8803d6278b450c](https://github.com/bsorrentino/langgraph4j/commit/e8803d6278b450c0fdb72b62eaaad2aea4b05da5))


### Refactor

 -  remove jdk17 modules ([3fe06d652cb065e](https://github.com/bsorrentino/langgraph4j/commit/3fe06d652cb065e14d18dccb18d17cb4dfbe2cfb))
   
 -  **core-jdk8**  Agent State Management ([7e19f1e8fc6e731](https://github.com/bsorrentino/langgraph4j/commit/7e19f1e8fc6e731a8def851e81100f05e752dbcf))
    > - AgentState from interface to concrete class
 > - AppendableValue a readonly interface
 > - Create internal AppendableValueRW to update state

 -  rename method ([4c196bff8442030](https://github.com/bsorrentino/langgraph4j/commit/4c196bff8442030c2ed65996bebcd3ce155e7b74))
    > addConditionalEdge to addConditionalEdges

 -  change packages layout ([f42c01b32eabcf4](https://github.com/bsorrentino/langgraph4j/commit/f42c01b32eabcf45cef67f8338b82ea65713139f))
   
 -  rewrite async utils ([a8851730971ebcd](https://github.com/bsorrentino/langgraph4j/commit/a8851730971ebcd5b0ef47c9c44b458541b0426d))
    > jdk8
 > jdk17 and above


### ALM 

 -  update artifactId ([5995f6e148cd8a6](https://github.com/bsorrentino/langgraph4j/commit/5995f6e148cd8a6d8bb14e7682a1fffd3a9f4d17))
   
 -  add utility scripts ([0ca2e51e3e746de](https://github.com/bsorrentino/langgraph4j/commit/0ca2e51e3e746de8b33cf34c37e78f6e6e298b1e))
   
 -  add .gitignore ([4cace7dbf3a4a93](https://github.com/bsorrentino/langgraph4j/commit/4cace7dbf3a4a93b70978f4eb113e2975ab53e6f))
   
 -  add sync-generator-jdk8 deps ([007a8fbbf24ac60](https://github.com/bsorrentino/langgraph4j/commit/007a8fbbf24ac60d5d5478477d7333be62adf625))
   
 -  upgrade langchain4j deps ([d80fdc6d9d38d86](https://github.com/bsorrentino/langgraph4j/commit/d80fdc6d9d38d867a2555de85d306bf570b068a3))
   






<!-- "name: v1.0-20240329" is a release tag -->

## [v1.0-20240329](https://github.com/bsorrentino/langgraph4j/releases/tag/v1.0-20240329) (2024-03-29)

### Features

 *  create modules for supporting jdk8 and jdk17 and above ([fa604bc1fbff4d8](https://github.com/bsorrentino/langgraph4j/commit/fa604bc1fbff4d8c5322ec8db6495a4acf22e09f))
   
 *  Enable agent to process more function calls ([9acbca23c35454d](https://github.com/bsorrentino/langgraph4j/commit/9acbca23c35454d1112fcdb524bb89268e334083))
   
 *  finalize developing langchain4j agentexecutor using langgraph4j ([7dd851cc9a63284](https://github.com/bsorrentino/langgraph4j/commit/7dd851cc9a632848609898c97561ec38a5900449))
   
 *  add AsyncIterator support ([ddac14de830e781](https://github.com/bsorrentino/langgraph4j/commit/ddac14de830e78164a146bdeb56912e47a4c9a33))
     > experimental feature
   
 *  implement workflow's run ([9a5b2e230aa652f](https://github.com/bsorrentino/langgraph4j/commit/9a5b2e230aa652f62fea669885894845b38c4e8b))
   
 *  initial implementation ([dc46c9b49847c52](https://github.com/bsorrentino/langgraph4j/commit/dc46c9b49847c52b6ff48d4414c0b23cfcf70352))
     > graph creation
     > graph compilation
   

### Bug Fixes

 -  check initial state value ([2d67f97b76f3a53](https://github.com/bsorrentino/langgraph4j/commit/2d67f97b76f3a53fd3fe9b0db409c08818340be1))

 -  Agent extend conversation with assistant's reply ([d9bf1a3e698a7e7](https://github.com/bsorrentino/langgraph4j/commit/d9bf1a3e698a7e71ace5d0b48046efe80f51727a))


### Documentation

 -  update readme ([bf5ba9fba8ff94d](https://github.com/bsorrentino/langgraph4j/commit/bf5ba9fba8ff94dc036faf3c60728df6422c51ae))


### Refactor

 -  finalize modules ([2a94541e46c4765](https://github.com/bsorrentino/langgraph4j/commit/2a94541e46c4765d953697106b50fb23db311c53))
    > jdk8
 > jdk17 and above

 -  finalize modules ([28380891947d7ba](https://github.com/bsorrentino/langgraph4j/commit/28380891947d7ba812ecd632faaae5773f0e9659))
    > jdk8
 > jdk17 and above

 -  remove var usage ([9d6b6eb7dabb7a6](https://github.com/bsorrentino/langgraph4j/commit/9d6b6eb7dabb7a6b0e11bb24ebdaf1a5a9db2620))
   
 -  **agents**  skip deployment ([2fcbf2224758ce0](https://github.com/bsorrentino/langgraph4j/commit/2fcbf2224758ce0ef0b97869f6614872de2f0835))
   
 -  update groupid ([11d601efd996d58](https://github.com/bsorrentino/langgraph4j/commit/11d601efd996d5892b7900ba46d970c46dcc2cdf))
   
 -  move DotEnvConfig in test ([4cfbd68a1edce63](https://github.com/bsorrentino/langgraph4j/commit/4cfbd68a1edce63f09a3dd7810f6ed91e4ad436d))
   
 -  move DotEnvConfig in test ([7038ecb4d8d833d](https://github.com/bsorrentino/langgraph4j/commit/7038ecb4d8d833d62783a71c33418923a679c30b))
   
 -  made AppendableValue immutable ([0ead59d7445d0b6](https://github.com/bsorrentino/langgraph4j/commit/0ead59d7445d0b66db1444e5788127d6f2c4c55b))
   
 -  use string block for prompt template ([ef5df2e9ae9d202](https://github.com/bsorrentino/langgraph4j/commit/ef5df2e9ae9d202bf7abb211d8041f9aabf80e6e))
   
 -  start developing langchain4j agentexecutor using langgraph4j ([cb3cf804a2a6257](https://github.com/bsorrentino/langgraph4j/commit/cb3cf804a2a625706bc1eb44fcb0f4f7a10f6e7f))
    > add maven multi-module layout
 > add module for demo
 > starting implements Agent class

 -  finalize AsyncIterator support ([f404e50f06e6832](https://github.com/bsorrentino/langgraph4j/commit/f404e50f06e6832afe25e024f6a8a61a8b270501))
    > experimental feature

 -  refine AsyncIterator support ([19b43fdb42bf64c](https://github.com/bsorrentino/langgraph4j/commit/19b43fdb42bf64ca0bfdbb32cf09d94d411eea07))
    > experimental feature

 -  refine AsyncIterator support ([e29517be766c0e3](https://github.com/bsorrentino/langgraph4j/commit/e29517be766c0e33cd30cab1f028af472d16eb71))
    > experimental feature

 -  create SyncSubmissionPublisher ([261b537494c1c5f](https://github.com/bsorrentino/langgraph4j/commit/261b537494c1c5f690fbac96c2fbe91b6a8000b6))
    > publishing already happen in a thread, seems not useful use an async submission

 -  update package tree ([f03f90780523832](https://github.com/bsorrentino/langgraph4j/commit/f03f90780523832a8ab20b9d89ab7efa9a4d9ee3))
   

### ALM 

 -  add distribution management info ([3a46d2676c56362](https://github.com/bsorrentino/langgraph4j/commit/3a46d2676c56362ec2aa9ea066b24fe56360626b))
   
 -  skip test on building ([ccaf2da369d3c4c](https://github.com/bsorrentino/langgraph4j/commit/ccaf2da369d3c4ca12bbceaac8bac5fa3ebeb69d))
   
 -  update git ignore ([7b6275466d06e04](https://github.com/bsorrentino/langgraph4j/commit/7b6275466d06e0437747aec25b16626cd50152a5))
   
 -  update ignore ([12218f697c9cfc1](https://github.com/bsorrentino/langgraph4j/commit/12218f697c9cfc1f0d60d3eece1e21611a3bd204))
   
 -  add git ignore ([b3c0a9ee7056bcd](https://github.com/bsorrentino/langgraph4j/commit/b3c0a9ee7056bcd35b05722bd7f9d397e3c9ff1e))
   

### Test 

 -  test refinements ([abead3b505119c3](https://github.com/bsorrentino/langgraph4j/commit/abead3b505119c314549389f112098a59e8c16c7))
   



