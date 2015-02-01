# Quanta

*A Clojure port of [Vignette](https://github.com/avibryant/vignette).*

Quanta is a distributed CRDT of keys and values.

Keys may be associated with values, which are sparse vectors of integers.
These vectors may be updated only via element-wise max. This constraint
yields a CRDT property because the max operation is idempotent, commutative,
and associative.

What is a data type with such contraints useful for?

A data structure with the above property can be used to construct vector
clocks, bloom filters, and hyperloglog. It is useful so long as values may be
represented as sparse vectors which grow monotonically.

## Status

Under development and totally unsuitable for production use!

## Installation

Currently, Quanta nodes are backed by LevelDB. Ensure LevelDB is installed on
your platform before running.

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
to a node will cause the node to return the vector in its entiritey:

```sh
$ curl -XPUT localhost:3000 \
       -d '{"k": "foo", "v": {}}' \
       -H 'content-type: application/json'
```

The response will contain the complete vector the node is currently aware of.

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
