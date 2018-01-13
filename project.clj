(defproject cassandra-event-store "0.1.0-SNAPSHOT"
  :description "Simple event store backed by Cassandra database"
  :url "https://github.com/amitayh/event-store"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.taoensso/nippy "2.14.0"]
                 [cc.qbits/alia-all "4.1.1"]
                 [cc.qbits/hayt "4.0.0"]
                 [docker-fixture "0.1.2"]])
