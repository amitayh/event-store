(ns cassandra-event-store.common)

(defrecord Event [stream-id version payload timestamp])

(defn- now [] (java.time.Instant/now))
