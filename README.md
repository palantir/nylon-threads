Nylon Threads
=============

Nylon is an artificial [fiber](https://en.wikipedia.org/wiki/Fiber_(computer_science)).

This library provides functinoality to transparently reuse a single pool of threads between
many executor objects, each of which may be configured with a separate concurrency limit,
queue size, and observability wrapper.

_We expect that this functionality will no longer be needed once
[Loom](https://openjdk.java.net/projects/loom/) becomes generally available._


Usage
-----

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


Gradle Tasks
------------
`./gradlew tasks` - to get the list of gradle tasks


Start Developing
----------------
Run one of the following commands:

* `./gradlew idea` for IntelliJ
* `./gradlew eclipse` for Eclipse
