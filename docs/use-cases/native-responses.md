# Native responses

## Caller-owned context and native responses

Use `broker.generateResponse(model, messages, tools, config)` to receive one
native gateway response without
executing tools, extending history or making a follow-up request. Assemble the
complete message array before each call. The broker traces the supplied request
and returned response; it does not read repository guidance or apply a context
policy.

The existing convenience completion method still executes tools and follows up.
Choose a serial or parallel runner according to the tools' effects. Parallel
execution does not make dependent edits safe.

Set `CompletionConfig(maxToolIterations = null)` to disable the tool-round
limit.
Existing finite defaults remain unchanged. Concurrency controls simultaneous
work; it is not a task budget or a loop detector.

Unknown tools produce ordered error outcomes. `ParallelToolRunner(maxConcurrency
= 4)` bounds active execution while retaining request order. Coroutine
cancellation propagates to child calls.

Native responses preserve the fields supplied by the gateway. Missing provider
usage or termination evidence must remain unknown; configured model names and
text length are not substitutes for reported metadata.
