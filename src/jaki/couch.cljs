(ns jaki.couch
  (:require [jaki.req :as req]
            [clojure.string :as string]
            [goog.array :as garr]
            [goog.json :as json]
            [goog.crypt.Sha1 :as sha1]))

;;
;; Global environmental variables for host, URL prefix and default database
;; along with setter methods
;;
(def host (atom nil))
(def url-prefix (atom ""))
(def default-db (atom nil))

(defn set-host! [s]
  (reset! host s))

(defn set-url-prefix [prefix]
  (reset! url-prefix (if (or (= "" prefix) (= "/" (.substr prefix 0 1))) prefix
                         (str "/" prefix))))

(defn set-default-db [db]
  (reset! default-db db))

(defn- url [path]
  (if-not (empty? @host)
    (str @host @url-prefix path)
    (str @url-prefix path)))

(defn- default-db-set? []
  (if (nil? @default-db) false true))

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

(defn new-uuid []
  (apply str (repeatedly 32 #(get "0123456789abcdef" (. js/Math (floor (rand 16)))))))

;; Note: doesn't account for chars > 1b, but neither does JS that ships with CouchDB, apparently
(defn- string-to-bytes [s]
  (let [get-byte #(if (string? %) (bit-and (.charCodeAt % 0) 0xFF) %)]
    (if (= 1 (count s))
      (garr/concat (get-byte s))
      (reduce #(garr/concat (get-byte %1) (get-byte %2)) s))))

(defn sign-up
  ([user-doc password] (sign-up user-doc password nil))
  ([user-doc password callback]
     (let [sha1 (goog.crypt.Sha1.) salt (new-uuid)
           id (or (:_id user-doc) (str "org.couchdb.user:" (:name user-doc)))
           roles (or (:roles user-doc) [])]
       (do (.update sha1 (string-to-bytes (str password salt)))
           (get-user-db (fn [db]
                          (post-docs (assoc user-doc :salt salt :_id id :type "user" :_roles roles
                                            :password_sha (.join (garr/map (. sha1 (digest)) #(.toString % 16)) ""))
                                     db callback)))))))

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

(defn create-db [name & [callback]]
  (req/put (url (str "/" name) callback)))

(defn drop-db [name & [callback]]
  (req/delete (url (str "/" name)) callback))

(defn about-db [name callback]
  (req/get (url (str "/" name)) callback))

(defn guess-current-db
  "Quick and dirty way to not have to specify any db, using pathname"
  []
  (aget (.split (.pathname (.location js/window)) "/")
        (+ 1 (- (.length (.split @url-prefix "/")) 1))))

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
    (if (empty? opts) ""
        (str "?" (apply str (interpose "&" (map (fn [[k v]]
                                                  (str (name k) "=" (js/encodeURIComponent v)))
                                                opts)))))))

(defn- resolve-db []
  (if (default-db-set?) @default-db (guess-current-db)))

(defn get-docs
  ([callback] (get-docs {:db (resolve-db) :include_docs true} callback))
  ([specifier callback]
     (cond (string? specifier) (req/get (url (str "/" (resolve-db) "/" specifier)) callback)
           (number? specifier) (get-docs {:db (resolve-db) :include_docs true :limit specifier} callback)
           (vector? specifier) (get-docs {:db (resolve-db) :include_docs true :keys specifier} callback)
           (map? specifier) (let [view-map (if (contains? specifier :db) specifier (assoc specifier :db (resolve-db)))
                                  uri (url (str (to-path view-map) (to-qstr view-map)))]
                              (if (:keys view-map) (req/post uri callback {:keys (:keys view-map)})
                                  (req/get uri callback))))))

(defn post-docs
  ([doc-or-docs] (post-docs doc-or-docs nil))
  ([doc-or-docs callback] (post-docs doc-or-docs (resolve-db) callback))
  ([doc-or-docs db callback]
     (let [data {:docs (if (vector? doc-or-docs) doc-or-docs (vector doc-or-docs))}]
       (req/post (url (str "/" db "/_bulk_docs")) callback data))))

(defn delete-docs
  ([doc-or-docs] (delete-docs doc-or-docs nil))
  ([doc-or-docs callback] (delete-docs (resolve-db) callback))
  ([doc-or-docs db callback]
     (if (vector? doc-or-docs)
       (req/post (url (str "/" db "/_bulk_docs")) callback (vec (for [d doc-or-docs] (assoc d :_deleted true))))
       (req/delete (url (str "/" db "/" (:_id doc-or-docs))) callback))))