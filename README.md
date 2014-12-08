# Quanta

Quanta is a Clojure port of [Vignette](https://github.com/avibryant/vignette).

A distributed CRDT where keys map to sparse vectors of integers. These vectors
are updated only via element-wise max. This property makes updates idempotent,
commutative, and associative--basic properties of a CRDT.

## Status

Under development and totally unsuitable for production use!

## Installation

Currently, Quanta nodes are backed by LevelDB. Ensure LevelDB is installed on
your platform before running.

## Usage

```sh
$ lein -- --addr localhost:3000 --peers localhost:3001
```

Details coming later.

## Design

See [Vignette](https://github.com/avibryant/vignette/blob/master/README.md).

## License

Copyright © 2014 Max Countryman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
