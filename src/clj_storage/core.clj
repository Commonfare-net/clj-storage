;; clj-storage - a minimal storage library

;; part of Decentralized Citizen Engagement Technologies (D-CENT)
;; R&D funded by the European Commission (FP7/CAPS 610349)

;; Copyright (C) 2017 Dyne.org foundation

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

(ns clj-storage.core)

(defprotocol Store
  (store! [s item params]
    "Store an item to storage s with params ")
  (update! [s update-fn]
    "Update the item found by running the update-fn on it and storing it")
  (query [s query params]
    "Items are returned using a query map")
  (delete! [s params]
    "Delete item(s) from a storage s")
  (aggregate [e formula params]
    "Process data aggragate and return computed results")) 

(defrecord MemoryStore [data]
  Store
  (store! [this k item]
    (do (swap! data assoc (k item) item)
        item))

  (update! [this k update-fn]
    (when-let [item (@data k)]
      (let [updated-item (update-fn item)]
        (swap! data assoc k updated-item)
        updated-item)))

  (fetch [this k] (@data k))

  (query [this query]
    (filter #(= query (select-keys % (keys query))) (vals @data)))

  (delete! [this k]
    (swap! data dissoc k))

  (delete-all! [this]
    (reset! data {}))

  (count-since [this date-time formula]
    ;; TODO: date time add
    (count (filter #(= formula (select-keys % (keys formula))) (vals @data)))))

(defn create-memory-store
  "Create a memory store"
  ([] (create-memory-store {}))
  ([data]
   ;; TODO: implement ttl and aggregation
   (MemoryStore. (atom data))))

(defn create-in-memory-stores [store-names]
  (zipmap
   (map #(keyword %) store-names)
   (repeat (count store-names) (create-memory-store))))

(defn empty-db-stores! [stores-m]
  (doseq [col (vals stores-m)]
    (delete-all! col)))
