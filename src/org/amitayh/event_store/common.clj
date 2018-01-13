(ns org.amitayh.event-store.common
  (:import (java.time Instant)))

(defrecord Event [stream-id version payload timestamp])

(defn now [] (Instant/now))
