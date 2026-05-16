# {{project-name | kebab_case}}

Spin HTTP + SQLite TODO app built with [scala-wasm](https://github.com/scala-wasm/scala-wasm).

## Requirements

- Java 17+
- sbt
- `wasmtime`
- [wasm-tools](https://github.com/bytecodealliance/wasm-tools)
- [wkg](https://github.com/bytecodealliance/wasm-pkg-tools)
- [wac](https://github.com/bytecodealliance/wac) (optional)
- `wit-bindgen` (requires our [fork](https://github.com/scala-wasm/wit-bindgen))
  - use `cargo install --git https://github.com/scala-wasm/wit-bindgen --branch scala`
- `spin` (requires canary)

scala-wasm emits wasm binary with features like GC, exception handling.
Stable Spin 4.0.0 does not expose the required `--experimental-wasm-feature` flag yet. These features are avaiable [only in canary build](https://github.com/spinframework/spin/pull/3377).

Install Spin canary:

```sh
curl -fsSL https://spinframework.dev/downloads/install.sh | bash -s -- -v canary
```

See https://spinframework.dev/v3/install

## WIT dependencies

Configure `wkg` registry mappings:

```sh
wkg config --edit
```

Add:

```toml
default_registry = "wa.dev"

[namespace_registries]
fermyon = "wa.dev"
wasi = "wa.dev"
```

## Build and run

```sh
wkg wit fetch
spin up --build \
  --experimental-wasm-feature gc \
  --experimental-wasm-feature exceptions \
  --experimental-wasm-feature function-references \
  --experimental-wasm-feature reference-types \
  --sqlite @migration.sql
```

(if you want to skip `fastLinkJS`, remove `--build`)

## API

```sh
curl -s http://127.0.0.1:3000/todos

curl -s http://127.0.0.1:3000/todos \
  -d '{"title":"Try Scala-wasm on Spin"}'

curl -s -X DELETE http://127.0.0.1:3000/todos/1
```

Plain text request bodies are accepted for `POST /todos` too.
