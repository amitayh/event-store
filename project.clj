(defproject org.amitayh/event-store "0.1.0-SNAPSHOT"
  :description "Simple event store backed by Cassandra"
  :url "https://github.com/amitayh/event-store"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.taoensso/nippy "2.14.0"]
                 [cc.qbits/alia-all "4.1.1"]
                 [cc.qbits/hayt "4.0.0"]
                 [org.amitayh/either "0.1.0-SNAPSHOT"]]
  :profiles {:test {:dependencies [[docker-fixture "0.1.2"]]}}
  :plugins [[lein-cloverage "1.0.10"]])
