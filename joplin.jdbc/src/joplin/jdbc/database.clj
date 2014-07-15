(ns joplin.jdbc.database
  (:use [joplin.core])
  (:require [ragtime.core :as ragtime]
            [ragtime.sql.files :as files]))

(defn- get-sql-migrations [path]
  (map verbose-migration (files/migrations path)))

(defn- get-db [target]
  (ragtime/connection (get-in target [:db :url])))

(defmethod migrate-db :jdbc [target & args]
  (ragtime/migrate-all
   (get-db target)
   (get-sql-migrations (:migrator target))))

(defmethod rollback-db :jdbc [target & [_ n]]
  (let [db (get-db target)]
    (doseq [m (get-sql-migrations (:migrator target))]
      (ragtime/remember-migration m))
    (ragtime/rollback-last db (or (when n (Integer/parseInt n))
                                  1))))

(defmethod seed-db :jdbc [target & args]
  (let [db (get-db target)
        migrations (set (map :id (get-sql-migrations (:migrator target))))
        applied (set (ragtime/applied-migration-ids db))
        seed-fn (load-var (:seed target))]

    (when (not= (count migrations) (count applied))
      (println "There are" (- (count migrations) (count applied)) "pending migrations")
      (println (clojure.set/difference migrations applied))
      (System/exit 1))
    (when-not seed-fn
      (System/exit 1))

    (apply seed-fn target args)))

(defmethod reset-db :jdbc [target & args]
  ;; Roll back all
  (while (not-empty (ragtime/applied-migration-ids (get-db target)))
    (apply rollback-db target args))

  ;; Migrate
  (apply migrate-db target args)

  ;; Seed
  (apply seed-db target args))

(defmethod create-migration :jdbc [target & args])