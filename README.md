# event-store [![Build Status](https://travis-ci.org/amitayh/event-store.svg?branch=master)](https://travis-ci.org/amitayh/event-store) [![codecov](https://codecov.io/gh/amitayh/event-store/branch/master/graph/badge.svg)](https://codecov.io/gh/amitayh/event-store)

Simple event store backed by Cassandra database. Can be used as the backbone of an event-sourced system

## Getting started

### Installation

Add the necessary dependency to your project:

[![Clojars Project](https://img.shields.io/clojars/v/org.amitayh/event-store.svg)](https://clojars.org/org.amitayh/event-store)

### API

The API includes 2 methods, for persisting and reading event streams. In order to use it, you will
need a [datastax session](http://docs.datastax.com/en/latest-java-driver-api/com/datastax/driver/core/Session.html)
for interacting with your Cassandra cluster. I recommend using [alia](https://github.com/mpenet/alia),
which is a nice Clojure wrapper for the Cassandra client.

#### Persist

Return value from the `persist-events` function follows the convention of returning a tuple:

`[value error]` - where `error` is `nil` in case the operation was successful, and `value` is `nil`
in case the operation failed (for more info, see [either-clj](https://github.com/amitayh/either-clj))

```clojure
(require '[org.amitayh.event-store.write :as write]
         '[qbits.alia :as alia])

; Connect to cluster
(def cluster ...)
(def session (alia/connect cluster))

; Partially apply session
(def persist-events (partial write/persist-events session))

; Persist some events
(def events [{:type :account-created}
             {:type :owner-changed :owner "John Doe"}
             {:type :deposit-performed :amount 50}])
             
(persist-events stream-id events)
; Returns persisted events:
;   [({:stream-id <some-uuid>
;      :version 1
;      :payload {:type :account-created}
;      :timestamp <some-timestamp>}
;     {:stream-id <some-uuid>
;      :version 2
;      :payload {:type :owner-changed :owner "John Doe"}
;      :timestamp <some-timestamp>}
;     {:stream-id <some-uuid>
;      :version 3
;      :payload {:type :deposit-performed :amount 50}
;      :timestamp <some-timestamp>}) nil]

; Optimistic locking is supported by supplying an optional `expected-version` arg:
(def event {:type :withdrawal-performed :amount 10})
(persist-events stream-id [event] 2) ; Returns `[nil :concurrent-modification]`, event not saved
(persist-events stream-id [event] 3) ; Succeeds, returns new event
```

#### Read

```clojure
(require '[org.amitayh.event-store.read :as read]
         '[qbits.alia :as alia])

; Connect to cluster
(def cluster ...)
(def session (alia/connect cluster))

; Partially apply session
(def read-events (partial read/read-events session))

; Fetch first 10 events, starting from version 1
(read-events stream-id 1 10)
; Returns persisted events:
;   ({:stream-id <some-uuid>
;     :version 1
;     :payload {:type :account-created}
;     :timestamp <some-timestamp>}
;    {:stream-id <some-uuid>
;     :version 2
;     :payload {:type :owner-changed :owner "John Doe"}
;     :timestamp <some-timestamp>}
;    {:stream-id <some-uuid>
;     :version 3
;     :payload {:type :deposit-performed :amount 50}
;     :timestamp <some-timestamp>}
;    {:stream-id <some-uuid>
;     :version 4
;     :payload {:type :withdrawal-performed :amount 10}
;     :timestamp <some-timestamp>})
```

## Schema

Below is the events table schema you need to create on your cluster:

```cql
CREATE TABLE IF NOT EXISTS events (
  stream_id    UUID,
  version      INT,
  payload      BLOB,
  timestamp    BIGINT,
  max_version  INT STATIC,
  PRIMARY KEY (stream_id, version)
);
```

## License

Copyright © 2018 Amitay Horwitz

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
