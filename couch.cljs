(ns jaki.couch
  (:require [jaki.req :as req]
            [clojure.string :as string]
            [goog.json :as json]))

(def *url-prefix* "")

(defn- url [path]
  (str *url-prefix* path))

(defn- encode-doc-id [id]
  (if (= "_design" (first (string/split id "/")))
    (str "_design/" (js/encodeURIComponent (subs id (.length "_design/"))))
    (js/encodeURIComponent id)))

;;
;; Configuration
;;
(comment (defn set-url-prefix [prefix]
           (reset! *url-prefix* prefix)))

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

(defn make-user-doc [])
(defn sign-up [user-doc password callback])

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
  (let [opts (select-keys view-map [:key :startkey :startkey_docid :endkey
                                    :endkey_docid :limit :stale :descending :skip
                                    :group :group_level :reduce :include_docs
                                    :inclusive_end :update_seq])]
    (if (empty? opts) ""
        (str "?" (apply str
                        (interpose "&"
                                   (map (fn [[k v]] (str (name k) "="
                                                         (js/encodeURIComponent v)))
                                        opts)))))))

(defn get-docs
  "Retrieves a view if design and view are specified in the view-map, otherwise all_docs"
  [view-map callback]
  (let [uri (url (str (to-path view-map) (to-qstr view-map)))]
    (if (:keys view-map)
      (req/post uri callback (:keys view-map))
      (req/get uri callback))))

(defn post-docs
  "Saves one or more docs to the database"
  []
  )

(comment
  (def person-view {:view "by-person"
                    :design "app"
                    :db "dev"
                    :descending true
                    :limit 10
                    :include_docs true})

  (get-docs person-view (fn [docs] (println docs)))

  )


;; Experimentation
(def parks {:db "nps" :design "units" :view "by-name-and-type"
            :reduce false :include_docs true})

(defn get-parks [rows]
  (map #(str (:key %) " - " (-> % :doc :location)) rows))

(defn show-parks [r]
  (doseq [park (get-parks (:rows r))]
    (clojure.browser.dom/log park)))

(get-docs parks show-parks)
