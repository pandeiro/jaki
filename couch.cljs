(ns jaki.couch
  (:require [jaki.req :as req]
            [clojure.string :as string]
            [goog.json :as json]))

;;
;; Global environmental variables for URL prefix and default database
;; along with setter methods
;;
(def *url-prefix* (atom ""))
(def *default-db* (atom nil))

(defn set-url-prefix [prefix]
  (reset! *url-prefix* (if (or (= "" prefix) (= "/" (.substr prefix 0 1))) prefix
                           (str "/" prefix))))

(defn set-default-db [db]
  (reset! *default-db* db))

(defn- url [path]
  (str @*url-prefix* path))

(defn- default-db-set? []
  (if (nil? @*default-db*) false true))

(defn- encode-doc-id [id]
  (if (= "_design" (first (string/split id "/")))
    (str "_design/" (js/encodeURIComponent (subs id (.length "_design/"))))
    (js/encodeURIComponent id)))

(defn config
  ([callback]
     (req/request (url "/_config") callback))
  ([section callback]
     (req/get (str (url "/_config/") section) callback))
  ([section option value-or-callback]
     (if (fn? value-or-callback) (req/request (str (url "/_config/") section "/" option)
                                              value-or-callback)
         (config section option value-or-callback nil)))
  ([section option value callback]
     (let [path (url "/_config")
           section (js/encodeURIComponent section)
           option (js/encodeURIComponent option)
           method (if (nil? value) "DELETE" "PUT")
           value (if (or (string? value) (number? value)) (str "\"" value "\"")
                     (str (json/serialize value)))
           headers {:Content-Type "application/json"}]
       (req/request (str path "/" section "/" option) callback method value headers))))

;;
;; Auth
;;
(defn get-session [callback]
  (req/get (url "/_session") callback))

(defn get-users-db [callback]
  (get-session #(callback (-> % :info :authentication_db))))

;; TODO
; (defn make-user-doc [])
; (defn sign-up [user-doc password callback])

(defn login
  ([username password]
     (login username password nil))
  ([username password callback]
     (req/post (url "/_session") callback {:name username :password password})))

(defn logout [callback]
  (req/delete (url "/_session") callback))

;;
;; Database
;;
(defn all-dbs [callback]
  (req/get (url "/_all_dbs") callback))

(defn create-db [name callback]
  (req/put (url (str "/" name) callback)))

(defn drop-db [name callback]
  (req/delete (url (str "/" name)) callback))

(defn about-db [name callback]
  (req/get (url (str "/" name)) callback))

(defn guess-current-db
  "Quick and dirty way to not have to specify any db, using pathname"
  []
  (aget (.split (.pathname (.location js/window)) "/")
        (+ 1 (- (.length (.split @*url-prefix* "/")) 1))))

;;
;; Documents
;;
(defn- to-path [view-map]
  (cond (every? (partial contains? view-map) [:db :design :view])
        (apply str (interleave ["/" "/_design/" "/_view/"]
                               (vals (select-keys view-map [:db :design :view]))))
        (contains? view-map :db)
        (str "/" (:db view-map) "/_all_docs")))

(defn- to-qstr [view-map]
  (let [opts (select-keys view-map [:key :startkey :startkey_docid :endkey :endkey_docid :limit
                                    :stale :descending :skip :group :group_level :reduce
                                    :include_docs :inclusive_end :update_seq])]
    (if (empty? opts)
      ""
      (str "?" (apply str (interpose "&" (map (fn [[k v]]
                                                (str (name k) "=" (js/encodeURIComponent v)))
                                              opts)))))))

(defn get-docs
  "Retrieves a view if db, design, and view are specified in the view-map, otherwise all_docs
  (optionally filtered by keys). If no viewmap is specified, defaults to returning all_docs
  with include_docs=true on default db (if specified) or current db derived from path"
  ([callback] (get-docs {:db (if (default-db-set?) @*default-db* (guess-current-db))
                         :include_docs true} callback))
  ([view-map callback]
     (let [uri (url (str (to-path view-map) (to-qstr view-map)))]
       (if (:keys view-map)
         (req/post uri callback (:keys view-map))
         (req/get uri callback)))))

(defn post-docs
  "Saves one or more docs to default, current, or specified database"
  ([doc-or-docs] (post-docs doc-or-docs #()))
  ([doc-or-docs callback] (post-docs doc-or-docs callback
                                     (if (default-db-set?) @*default-db* (guess-current-db))))
  ([doc-or-docs db callback]
     (let [data {:docs (if (vector? doc-or-docs) doc-or-docs (vector doc-or-docs))}]
       (req/post (url "/" db "/_bulk_docs") callback data))))

