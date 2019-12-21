;; clj-storage - a minimal storage library

;; part of Decentralized Citizen Engagement Technologies (D-CENT)
;; R&D funded by the European Commission (FP7/CAPS 610349)

;; Copyright (C) 2017- Dyne.org foundation

;; Sourcecode designed, written and maintained by
;; Aspasia Beneti  <aspra@dyne.org>

;; This program is free software: you can redistribute it and/or modify
;; it under the terms of the GNU Affero General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or
;; (at your option) any later version.

;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU Affero General Public License for more details.

;; You should have received a copy of the GNU Affero General Public License
;; along with this program.  If not, see <http://www.gnu.org/licenses/>.

(ns clj-storage.core
  (:require [clj-storage.spec]
            [clojure.spec.alpha :as spec]
            [taoensso.timbre :as log]))

(defprotocol Store
  (store! [s item]
    "Store an item to storage s")
  (update! [s query update-fn]
    "Update all items found by with the update-fn, specific to the implementation")
  (query [s query pagination]
    "Find one or more items given a query map (does a fetch when query map is only id). Pagination will be used if not empty")
  (delete! [s item]
    "Delete item from a storage s")
  (aggregate [s formula params]
    "Process data aggregate and return computed results")
  (add-index [s index unique]
    "Add an index to a storage s. Unique can be true or false")
  (expire [s seconds]
    "Expire items of this storage after seconds")) 

(defrecord MemoryStore [data]
  Store
  (store! [this item]
    (let [id (or (:id item)
                 (str (java.util.UUID/randomUUID)))]
      (swap! data assoc id (assoc item :id id))
      item))

  (update! [this q update-fn]
    (let [items (query this q {})]
      (doseq [item items]
        (log/info "ITEM: "item)
        (swap! data update-in [(:id item)] update-fn))))

  ;; TODO add pagination
  (query [this query pagination]
    (if (spec/valid? :clj-storage.spec/only-id-map query)
      (@data (:id query))
      (filter #(= query (select-keys % (keys query))) (vals @data))))

  (delete! [this item]
    (swap! data dissoc (:id item)))

  ;; TODO: maybe add as wrapper function?
  #_(delete-all! [this]
    (reset! data {}))

  ;; TODO: add aggregate?
  #_(count-since [this date-time formula]
    ;; TODO: date time add
    (count (filter #(= formula (select-keys % (keys formula))) (vals @data)))))

(defn create-memory-store
  "Create a memory store"
  ([] (create-memory-store {}))
  ([data]
   ;; TODO: implement ttl and aggregation
   (MemoryStore. (atom data))))

(defn create-in-memory-stores
  [store-names]
  (let [names (spec/assert ::in-memory-store-names store-names)]
    (zipmap
     (map #(keyword %) names)
     (repeat (count names) (create-memory-store)))))

(spec/fdef create-in-memory-stores :args (spec/cat :store-names (spec/coll-of string?)))
;; TODO
#_(defn empty-db-stores! [stores-m]
  (doseq [col (vals stores-m)]
    (delete-all! col)))

;; TODO extract conf
(spec/check-asserts true)
