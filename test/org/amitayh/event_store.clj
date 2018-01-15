(ns org.amitayh.event-store
  (:import (com.datastax.driver.core.exceptions NoHostAvailableException)
           (java.util UUID))
  (:require [clojure.test :refer :all]
            [org.amitayh.either :refer :all]
            [org.amitayh.event-store.schema :as schema]
            [org.amitayh.event-store.write :as w]
            [org.amitayh.event-store.read :as r]
            [docker.fixture :as docker]
            [qbits.alia :as alia]
            [qbits.hayt :as hayt]))

(def port (docker/rand-port))

(def host (docker/host))

(def session (atom nil))

(def ^:dynamic persist-events)

(def ^:dynamic read-events)

(defn load-schema [session]
  (alia/execute session
    (hayt/create-keyspace :event_store
      (hayt/with {:replication {:class "SimpleStrategy" :replication_factor 1}})))
  (alia/execute session (hayt/use-keyspace :event_store))
  (alia/execute session schema/schema))

(defn connect []
  (try
    (let [options {:contact-points [host] :port (Integer/parseInt port)}
          cluster (alia/cluster options)
          session_ (alia/connect cluster)]
      (println "connected to" (str host ":" port))
      (load-schema session_)
      (reset! session session_))

    (catch NoHostAvailableException e
      :no-host-available)))

(defn random-stream-id []
  (UUID/randomUUID))

(defn version-and-payload [event]
  (select-keys event [:version :payload]))

(defn same-events [actual expected]
  (= (map version-and-payload actual)
     expected))

(def cmd ["docker" "run" "-d" "-p" (str port ":9042") "cassandra:latest"])

(defn init-fn [component]
  (loop [retries 5]
    (let [session (connect)]
      (when (and (pos? retries) (= session :no-host-available))
        (println "waiting for server...")
        (Thread/sleep 5000)
        (recur (dec retries))))))

(use-fixtures :once (docker/new-fixture {:cmd cmd :init-fn init-fn}))

(use-fixtures :each
  (fn [test]
    (binding [persist-events (partial w/persist-events @session)
              read-events (partial r/read-events @session)]
      (test))))

(deftest cassandra-event-store-test
  (testing "return empty stream if no events were saved"
    (is (empty? (read-events (random-stream-id) 1 10))))

  (testing "persist zero events"
    (is (= (success []) (persist-events (random-stream-id) []))))

  (testing "persist event for correct stream ID"
    (let [stream-id (random-stream-id)
          other-stream-id (random-stream-id)]
      (persist-events stream-id [:foo])
      (is (empty? (read-events other-stream-id 1 10)))))

  (testing "persist multiple events"
    (let [stream-id (random-stream-id)
          result (persist-events stream-id [:foo :bar :baz])]
      (is (same-events (first result)
                       [{:version 1 :payload :foo}
                        {:version 2 :payload :bar}
                        {:version 3 :payload :baz}]))))

  (testing "keep old events"
    (let [stream-id (random-stream-id)]
      (persist-events stream-id [:foo])
      (persist-events stream-id [:bar])
      (is (same-events (read-events stream-id 1 10)
                       [{:version 1 :payload :foo}
                        {:version 2 :payload :bar}]))))

  (testing "persist events with expected version"
    (let [stream-id (random-stream-id)
          result1 (persist-events stream-id [:foo] nil)
          result2 (persist-events stream-id [:bar] 1)]
      (is (same-events (first result1)
                       [{:version 1 :payload :foo}]))
      (is (same-events (first result2)
                       [{:version 2 :payload :bar}]))))

  (testing "fail if expected version doesn't match"
    (let [stream-id (random-stream-id)]
      (persist-events stream-id [:foo] nil)
      (is (= (persist-events stream-id [:bar] nil)
             (failure :concurrent-modification)))))

  (testing "not allow version gaps"
    (testing "for first event"
      (let [stream-id (random-stream-id)]
        (is (= (persist-events stream-id [:foo] 1)
               (failure :concurrent-modification)))))

    (testing "for subsequent events"
      (let [stream-id (random-stream-id)]
        (persist-events stream-id [:foo] nil)
        (is (= (persist-events stream-id [:bar] 2)
               (failure :concurrent-modification))))))

  (testing "persist events atomically"
    (let [stream-id (random-stream-id)]
      (persist-events stream-id [:foo] nil)
      (persist-events stream-id [:bar :baz] nil) ; Should fail
      (is (same-events (read-events stream-id 1 10)
                       [{:version 1 :payload :foo}]))))

  (testing "fetch events from correct position"
    (let [stream-id (random-stream-id)]
      (persist-events stream-id [:foo :bar :baz :qux])
      (is (same-events (read-events stream-id 1 3)
                       [{:version 1 :payload :foo}
                        {:version 2 :payload :bar}
                        {:version 3 :payload :baz}]))
      (is (same-events (read-events stream-id 4 3)
                       [{:version 4 :payload :qux}]))))

  (testing "allow fetching from beginning of stream with version 0 (same as from version 1)"
    (let [stream-id (random-stream-id)]
      (persist-events stream-id [:foo :bar])
      (is (same-events (read-events stream-id 0 1)
                       [{:version 1 :payload :foo}])))))
