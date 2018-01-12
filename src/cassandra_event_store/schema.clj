(ns cassandra-event-store.schema
  (:require [qbits.hayt :as hayt]))

(def schema
  (hayt/create-table :events
    (hayt/if-not-exists)
    (hayt/column-definitions
      [[:stream_id :uuid]
       [:version :int]
       [:payload :blob]
       [:timestamp :bigint]
       [:max_version :int :static]
       [:primary-key [:stream_id :version]]])))
