(ns cassandra-event-store.write
  (:require [cassandra-event-store.common :refer :all]
            [qbits.alia :as alia]
            [taoensso.nippy :as nippy]
            [qbits.hayt :as hayt]))

(defn- select-max-version [stream-id]
  (hayt/select :events
    (hayt/columns :max_version)
    (hayt/where [[:stream_id stream-id]])
    (hayt/limit 1)))

(defn- update-max-version [stream-id max-version expected-version]
  (hayt/update :events
    (hayt/set-columns :max_version max-version)
    (hayt/where [[:stream_id stream-id]])
    (hayt/only-if [[:max_version expected-version]])))

(defn- insert [stream-id event]
  (hayt/insert :events
    (hayt/values {:stream_id stream-id
                  :version (:version event)
                  :payload (-> event :payload nippy/freeze)
                  :timestamp (-> event :timestamp .toEpochMilli)})
    (hayt/if-exists false)))

(defn- stream-max-version [session stream-id]
  (let [query (select-max-version stream-id)
        result (alia/execute session query)]
    (-> result first :max_version)))

(defn- to-events [stream-id payloads expected-version]
  (let [timestamp (now)
        to-event (fn [index payload]
                   (let [version (+ expected-version index 1)]
                     (->Event stream-id version payload timestamp)))]
    (map-indexed to-event payloads)))

(defn- last-event-version [events]
  (-> events last :version))

(defn- create-batch [session stream-id events expected-version]
  (let [max-version (last-event-version events)
        update-version (update-max-version stream-id max-version expected-version)
        insert-to-stream (partial insert stream-id)
        inserts (map insert-to-stream events)]
    (alia/batch (conj inserts update-version))))

(def ^:private applied (keyword "[applied]"))

(defn- was-applied? [result]
  (-> result first applied))

(defn persist-events
  "Persist events to stream"

  ([session stream-id payloads]
   (loop [retries 5
          expected-version (stream-max-version session stream-id)]
     (let [result (persist-events session stream-id payloads expected-version)]
       (if (and (> retries 0) (= result :concurrent-modification))
         (recur (dec retries) (stream-max-version session stream-id))
         result))))

  ([session stream-id payloads expected-version]
   (if (empty? payloads)
     []
     (let [events (to-events stream-id payloads (or expected-version 0))
           batch (create-batch session stream-id events expected-version)
           result (alia/execute session batch)]
       (if (was-applied? result)
         events
         :concurrent-modification)))))
