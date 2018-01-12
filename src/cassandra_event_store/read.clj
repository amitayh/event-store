(ns cassandra-event-store.read
  (:require [cassandra-event-store.common :refer :all]
            [qbits.alia :as alia]
            [taoensso.nippy :as nippy]
            [qbits.hayt :as hayt]))

(defn- select-events [stream-id from-version max-count]
  (hayt/select :events
    (hayt/columns :version :payload :timestamp)
    (hayt/where [[:stream_id stream-id]
                 [>= :version from-version]])
    (hayt/order-by [:version])
    (hayt/limit max-count)))

(defn- to-event [stream-id row]
  (->Event
    stream-id
    (:version row)
    (-> row :payload .array nippy/thaw)
    (-> row :timestamp java.time.Instant/ofEpochMilli)))

(defn read-events [session stream-id from-version max-count]
  "Read events from stream `stream-id`.
  Returns `max-count` events, starting from `from-version`"

  (let [query (select-events stream-id from-version max-count)
        result (alia/execute session query {:fetch-size max-count})
        to-event (partial to-event stream-id)]
    (map to-event result)))
