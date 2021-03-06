;; clj-storage - Minimal storage library based on an abstraction which helps seamlessly switching between DBs

;; part of Decentralized Citizen Engagement Technologies (D-CENT)
;; R&D funded by the European Commission (FP7/CAPS 610349)

;; Copyright (C) 2019- Dyne.org foundation

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

(ns clj-storage.db.redis
  (:require [taoensso.carmine :as car :refer (wcar)]

            [clojure.spec.alpha :as spec]
            [clj-storage.spec]
            
            [clj-storage.core :as storage :refer [Store]]
            [clj-storage.config :as conf]
            
            [taoensso.timbre :as log]))

(defmacro wcar* [conn & body] `(car/wcar ~@conn ~@body))

(defrecord RedisStore [redis-conn]
  Store
  (store! [database item]
    (spec/assert ::item item)
    (wcar* (:conn database) (car/set (:key item) (:value item))))
  
  (update! [database query update-fn]
    (if (spec/valid? :clj-storage.spec/only-key-map query)
      (let [k (:key query)]
        (wcar* (:conn database) (car/atomic {} (conf/redis-atomic-retries (conf/create-config)) ; Retry <= 100 times on failed optimistic lock, or throw ex

                                            (car/watch k) ; Watch key for changes
                                            (let [;; You can grab the value of the watched key using
                                                  ;; `with-replies` (on the current connection), or
                                                  ;; a nested `wcar` (on a new connection):
                                                  curr-val (car/with-replies (car/get k))]

                                              (car/return curr-val)

                                              (car/multi) ; Start the transaction
                                              (car/set k (update-fn curr-val))
                                              (car/get k)))))
      (when (spec/valid? :clj-storage.spec/multiple-keys query)
        (wcar* (:conn database) (car/atomic {} (conf/redis-atomic-retries (conf/create-config)) ; Retry <= 100 times on failed optimistic lock, or throw ex

                                            (car/watch (:keys query)) ; Watch key for changes
                                            (let [;; You can grab the value of the watched key using
                                                  ;; `with-replies` (on the current connection), or
                                                  ;; a nested `wcar` (on a new connection):
                                                  curr-vals (car/with-replies (apply car/mget (:keys query)))]

                                              (car/return curr-vals)

                                              (car/multi) ; Start the transaction

                                              (apply car/mset (interleave (:keys query) (map (fn [val] (update-fn val)) curr-vals)))
                                              (car/mget (:keys query))))))))

  (query [database query pagination]
    ;; pagination for redis is only applicable for specific commands check https://christophermcdowell.dev/post/pagination-with-redis/
    (if (spec/valid? :clj-storage.spec/only-key-map query)
      (wcar* (:conn database) (car/get (:key query)))
      (if (spec/valid? :clj-storage.spec/multiple-keys query)
        (wcar* (:conn database) (apply car/mget (:keys query)))
        nil)))
  
  (delete! [database item]
    (spec/assert :clj-storage.spec/only-key-map item)
    (wcar* (:conn database) (car/del (:key item))))

  (aggregate [database formula params]
    (log/warn "Not implemented yet. Redis aggregates are specific to specific types like sorted sets https://redis.io/commands/zunionstore. Another option is lua scripts like https://github.com/ptaoussanis/carmine#lua."))

  (add-index [database index params]
    (spec/assert ::index-params params)
    (wcar* (:conn database) (car/zadd index (:score params) (:member params))))

  (expire [database seconds params]
    (spec/assert :clj-storage.spec/multiple-keys params)
    (wcar* (:conn database) (mapv #(car/expire % seconds) (:keys params)))))

(defn count-keys [database]
  (wcar* (:conn database) (car/dbsize)))

(defn get-all-keys [database]
  (wcar* (:conn database) (car/keys "*")))

(defn count-sorted-set [database key]
  (wcar* (:conn database) (car/zcard key)))

(defn create-redis-database [uri]
  (let [conn {:pool {} :spec {:uri uri}}]
    {:store (RedisStore. conn)
     :conn conn}))

(spec/check-asserts (conf/spec-asserts (conf/create-config)))
