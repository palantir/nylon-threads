Nylon Threads
=============

Nylon is an artificial [fiber](https://en.wikipedia.org/wiki/Fiber_(computer_science)).

This library provides functionality to transparently reuse a single pool of threads between
many executor objects, each of which may be configured with a separate concurrency limit,
queue size, and observability wrapper.

_We expect that this functionality will no longer be needed once
[Loom](https://openjdk.java.net/projects/loom/) becomes generally available._


Usage
-----

### NylonExecutor

The `NylonExecutor` utility allows a common cached executor to be shared between consumers
with different constraints, with behavior matching a cached or fixed-size executor, using
a configurable-sized queue. This utility handles renaming threads when they're in use such
that the thread-names match what would be expected of an isolated (non-shared) pool.

This is important in applications which use several executors, often work is passed through
several stages, each of which rely on an executor. When these executors each use their own
thread pool, sufficient threads must be available for all stages, despite the fact that
all stages are not (and often cannot be) saturated at all times. Threads use a fairly large
amount of memory, are expensive to create and park, and impact garbage collectors in
unexpected ways, so it's advantageous to reuse a smaller pool.

```java
// Core threadpool is only used to create NylonExecutor views
Executor threadPool = Executors.newCachedThreadPool();

// All code interacts with these views
        
// executorOne emulates a cached executor, it has
// no thread limit. This is useful because it can be
// wrapped with metrics, tracing, and logging.
ExecutorService executorOne = NylonExecutor.builder()
    .name("one")
    .executor(threadPool)
    .build();

// executorTwo emulates a fixed-size executor, it has
// a thread limit and maximum queue size, despite the
// thread pool itself providing neither of these features.
ExecutorService executorTwo = NylonExecutor.builder()
    .name("two")
    .executor(threadPool)
    .maxThreads(5)
    .queueSize(100)
    .build();
```

### ThreadNames

The [`ThreadNames`](nylon-threads/src/main/java/com/palantir/nylon/threads/ThreadNames.java)
utility allows thread names to be updated just like `Thread.setName`, however it only updates
the name used by Java, not [the OS thread name](https://man7.org/linux/man-pages/man3/pthread_setname_np.3.html)
which can have unexpected costs in some configurations. For example, `cgrulesengd` may handle
events each time a thread is renamed, potentially far more expensive than the update operation
and syscall themselves.

This may reduce the utility of some observability tools which interact with generic processes,
however JVM-based tools like jstack and logging frameworks continue to use the java thread name
as expected.

### VirtualThreads

The [`ThreadNames`](nylon-threads/src/main/java/com/palantir/nylon/threads/VirtualThreads.java)
utility allows virtual threads to be safely used from libraries which target older jdk bytecode
when run using a jdk-21+ runtime.

VirtualThreads provides a static utility method to detect virtual threads:
```java
boolean virtual = VirtualThreads.isVirtual(Thread.currentThread())
```

As well as an API shim over `Thread.ofVirtual()`:
```java
VirtualThreadSupport support = VirtualThreads.get().orElseThrow();
ExecutorService executor = support.newThreadPerTaskExecutor(support.ofVirtual().factory());
```

Gradle Tasks
------------
`./gradlew tasks` - to get the list of gradle tasks


Start Developing
----------------
Run one of the following commands:

* `./gradlew idea` for IntelliJ
* `./gradlew eclipse` for Eclipse
