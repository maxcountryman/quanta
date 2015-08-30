# Quanta

*A Clojure port of [Vignette](https://github.com/avibryant/vignette).*

[![Build Status](https://travis-ci.org/maxcountryman/quanta.svg?branch=master)](https://travis-ci.org/maxcountryman/quanta)

Quanta is a distributed, highly-available, eventually-consistent sketch
database.

Quanta is a specialized key-value store, where keys are strings and values are
sparse integer vectors. Values can only be modified with element-wise max.
This limitation ensures a [CRDT](https://en.wikipedia.org/wiki/Conflict-free_replicated_data_type)
property--the `max` operation is associative, commutative, and idempotent. That
means that updates can be performed in any order, in any combination, and as 
many times as we want without inconsistency.

While this might seem like a limitation which precludes usefulness, a
surprising number of interesting applications can be implemented over these
constraints: bloom filters, vector clocks, and hyperloglog are all examples of
applications that can be easily implemented using Quanta.

## Status

Under development and totally unsuitable for production use!

I'm building this as a way to learn about distributed systems, so you should
not necessarily expect this to ever be fit for use in a realworld system.

## Installation

Currently, Quanta nodes are backed by LevelDB. Ensure LevelDB is installed on
your platform before running.

Quanta is available on Clojars.

[![Clojars Project](http://clojars.org/quanta/latest-version.svg)](http://clojars.org/quanta)

## Usage

To run a node with two known peers:

```sh
$ lein run --node-addr 'localhost:3000' --peers 'localhost:3001 localhost:3002'
```

This will set up a node listening on localhost:3000. It will expect to find
peer nodes on localhost:3001 and localhost:3002. As the node starts it will
bootstrap itself by requesting a peerlist from the given peers.

### HTTP Interface

Each node is accessible via a simple REST-like HTTP interface.

To set a key in the node, a PUT request may be used. The body is a JSON
representation of the vector to be stored or updated. Using curl as an example
client:

```sh
$ curl -XPUT localhost:3000 \
       -d '{"k": "foo", "v": {"0": 42}}' \
       -H 'content-type: application/json'
```

This associates the key "foo" with the vector `{0 42}` in the node. If a vector
already exists with the key "foo" then its indexes which match the provided
indexes will be updated via element-wise max. Any missing indexes or indexes
with larger values than provided will be return in the response as a
JSON-encoded vector.

Retrieving a key is a special case of setting a key. Sending an empty vector
to a node will cause the node to return the vector in its entirety:

```sh
$ curl -XPUT localhost:3000 \
       -d '{"k": "foo", "v": {}}' \
       -H 'content-type: application/json'
```

The response will contain the complete vector the node is currently aware of.
This will look something like:

```json
[
  {
    "k": "foo",
    "v": {
      "0": 42
    },
    "ttl": 0
  }
]
```

Finally the same method can be used for searches and aggregates. For example,
we can retrieve a list of vectors for all keys prefixed with "foo":

```sh
$ curl -XPUT localhost:3000 \
       -d '{"k": "foo*", "v": {}}' \
       -H 'content-type: application/json'
```

## Design

See [Vignette](https://github.com/avibryant/vignette/blob/master/README.md).

## License

Copyright © 2014 Max Countryman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
