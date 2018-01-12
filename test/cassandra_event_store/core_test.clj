(ns cassandra-event-store.core-test
  (:require [clojure.test :refer :all]
            [cassandra-event-store.core :refer :all]
            [cassandra-event-store.schema :as schema]
            [cassandra-event-store.write :as w]
            [cassandra-event-store.read :as r]
            [docker.fixture :as docker]
            [qbits.alia :as alia]
            [qbits.hayt :as hayt]))

(def host (docker/host))

(def session (atom nil))

(def ^:dynamic persist-events)

(def ^:dynamic read-events)

(def ^:dynamic stream-id)

(defn load-schema [session]
  (alia/execute session
    (hayt/create-keyspace :event_store
      (hayt/with {:replication {:class "SimpleStrategy" :replication_factor 1}})))
  (alia/execute session (hayt/use-keyspace :event_store))
  (alia/execute session schema/schema))

(defn connect []
  (try
    (let [cluster (alia/cluster {:contact-points [host]})
          session_ (alia/connect cluster)]
      (println "connected to" (str host ":9042"))
      (load-schema session_)
      (reset! session session_))

    (catch com.datastax.driver.core.exceptions.NoHostAvailableException e
      :no-host-available)))

(def cmd ["docker" "run" "-d" "-p" "9042:9042" "cassandra:latest"])

(defn init-fn [component]
  (loop [retries 5]
    (let [_session (connect)]
      (when (and (pos? retries) (= _session :no-host-available))
        (println "waiting for server...")
        (Thread/sleep 5000)
        (recur (dec retries))))))

(use-fixtures :once (docker/new-fixture {:cmd cmd :init-fn init-fn}))

(use-fixtures :each
  (fn [test]
    (binding [persist-events (partial w/persist-events @session)
              read-events (partial r/read-events @session)
              stream-id (java.util.UUID/randomUUID)]
      (test))))

(deftest cassandra-event-store-test
  (testing "return empty stream if no events were saved"
    (is (empty? (read-events stream-id 1 10))))

  (testing "persist zero events"
    (is (= [] (persist-events stream-id []))))

  (testing "persist one event"
    (persist-events stream-id [:foo])
    (is (= [:foo] (read-events stream-id 1 10)))))
