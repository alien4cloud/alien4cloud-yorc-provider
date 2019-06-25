# Plugin Yorc

## Spring contexts

## Spring State Machine

## Asynchronous design

The plug in is working with asynchronous requests. We use the Apache HTTP Async client to make all HTTP requests to Yorc. This use the Reactor pattern for dispatching request responses. In this model, the reactor use a single thread to handle io events and dispatches HTTP responses on a dedicated thread pool.

The reactor is configured by the **alien4cloud.paas.yorc.context.TemplateManager#configured** method.

We also use HTTP connection pooling. There is a eviction task that is run periodically to close unused connections.

## Reactive Design

We use [RxJava](https://github.com/ReactiveX/RxJava) to handle asynchronous events (our HTTP responses) and to build buses of events between our different beans.

## Threading Model

There is 2 thread pools by orchestrator context

- IO Thread Pool

This pool is used by the reactor. All HTTP response are subscribed on threads from this pool.

- Executor Thread Pool

The plug-in use this pool for all its long tasks. As a general rule, long jobs must be avoided on the io pool for maximum efficiency of the reactor.

## Buses

The plugin creates 3 kinds of buses:

### Log Event Bus

The LogEventPollingService is publishing log events from Yorc on this bus. There is one bus of this kind by orchestrator. It s shared among all deployments. LogEventService is the only subscriber.

### Event Bus

The EventPollingService is publishing events from Yorc on this bus. There is one bus of this type per deployment. 

Subscribers of this bus are:

- InstanceInformationService
- WorkflowInformationService

There's also a connection between the event bus and the message bus. When a Yorc event is received, it s converted to Message\<FsmEvent\> and re-injected in the message bus

### Message Bus

Message on this bus are subscribed by the StateMachineService. They triggers transitions within the FSM.  There is one bus of this type per deployment. 

This bus has 2 main sources:

- Message created from the event that comes from Event Bus
- Direct publish in the code (UI actions, response from an http request, etc)



