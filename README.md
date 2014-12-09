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

```sh
$ lein run --addr 'localhost:3000' --peers 'localhost:3001 localhost:3002'
```

Details coming later.

## Design

See [Vignette](https://github.com/avibryant/vignette/blob/master/README.md).

## License

Copyright © 2014 Max Countryman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
