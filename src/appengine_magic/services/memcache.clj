(ns appengine-magic.services.memcache
  (:refer-clojure :exclude (contains? get))
  (:import [com.google.appengine.api.memcache MemcacheService MemcacheServiceFactory
            MemcacheService$SetPolicy]))


(defonce *memcache-service* (atom nil))
(defonce *namespaced-memcache-services* (atom {}))


(defonce *policy-type-map*
  {:always MemcacheService$SetPolicy/SET_ALWAYS
   :add-if-not-present MemcacheService$SetPolicy/ADD_ONLY_IF_NOT_PRESENT
   :replace-only MemcacheService$SetPolicy/REPLACE_ONLY_IF_PRESENT})


(defn get-memcache-service [& {:keys [namespace]}]
  (if (nil? namespace)
      (do (when (nil? @*memcache-service*)
            (reset! *memcache-service* (MemcacheServiceFactory/getMemcacheService)))
          @*memcache-service*)
      (let [s (@*namespaced-memcache-services* namespace)]
        (if-not (nil? s)
            s
            ((swap! *namespaced-memcache-services* assoc
                    namespace (MemcacheServiceFactory/getMemcacheService namespace))
             namespace)))))


(defrecord Statistics [bytes-returned-for-hits
                       hit-count
                       item-count
                       max-time-without-access
                       miss-count
                       total-item-bytes])


(defn statistics [& {:keys [namespace]}]
  (let [stats (.getStatistics (get-memcache-service :namespace namespace))]
    (Statistics. (.getBytesReturnedForHits stats)
                 (.getHitCount stats)
                 (.getItemCount stats)
                 (.getMaxTimeWithoutAccess stats)
                 (.getMissCount stats)
                 (.getTotalItemBytes stats))))


(defn clear-all
  "Clears the entire cache. Does not respect namespaces!"
  []
  (.clearAll (get-memcache-service)))


(defn contains? [key & {:keys [namespace]}]
  (.contains (get-memcache-service :namespace namespace) key))


(defn delete
  "If (sequential? key), deletes in batch."
  [key & {:keys [namespace millis-no-readd]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if millis-no-readd
        (if (sequential? key)
            (.deletAll service key millis-no-readd)
            (.delete service key millis-no-readd))
        (if (sequential? key)
            (.deleteAll service key)
            (.delete service key)))))


(defn get
  "If (sequential? key), returns values as a map."
  [key & {:keys [namespace]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if (sequential? key)
        (into {} (.getAll service key))
        (.get service key))))


(defn put [key value & {:keys [namespace expiration policy]
                        :or {policy :always}}]
  (let [service (get-memcache-service :namespace namespace)
        policy (*policy-type-map* policy)]
    (.put service key value expiration policy)))


(defn put-map [values & {:keys [namespace expiration policy]
                         :or {policy :always}}]
  (let [service (get-memcache-service :namespace namespace)
        policy (*policy-type-map* policy)]
    (.putAll service values expiration policy)))


(defn increment
  "If (sequential? key), increment each key by the delta."
  [key delta & {:keys [namespace initial]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if initial
        (if (sequential? key)
            (.incrementAll service key delta (long initial))
            (.increment service key delta (long initial)))
        (if (sequential? key)
            (.incrementAll service key delta)
            (.increment service key delta)))))


(defn increment-map [values & {:keys [namespace initial]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if initial
        (.incrementAll service values (long initial))
        (.incrementAll service values))))