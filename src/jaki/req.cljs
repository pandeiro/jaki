(ns jaki.req
  (:use [jaki.util :only [clj->js]])
  (:require [goog.net.XhrIo :as xhr]
            [goog.json :as json])
  (:refer-clojure :exclude [get]))

;; For some odd reason, goog.json.parse uses eval, which is not allowed in Chrome
;; extensions and is generally evil. So if native JSON support exists (it's 2012),
;; use that.
(defn- parse-json [s]
  (if js/JSON (.parse js/JSON s) (json/parse s)))

(defn request
  "Carries out an HTTP request, converting any payload from Clojure map to JSON string,
  and passes JSON string response to callback as a Clojure map."
  ([url]
     (request url nil "GET" nil nil))
  ([url callback]
     (request url callback "GET" nil nil))
  ([url callback method]
     (request url callback method nil nil))
  ([url callback method payload]
     (request url callback method payload nil))
  ([url callback method payload headers]
     (let [do-callback (when (fn? callback)
                         (fn [e]
                           (callback (js->clj (parse-json (.getResponseText (.-target e)))
                                              :keywordize-keys true))))
           payload (if (or (string? payload) (number? payload) (nil? payload)) payload
                       (json/serialize (if (or (map? payload) (coll? payload))
                                         (clj->js payload)
                                         payload)))
           headers (if (map? headers) (clj->js headers) headers)]
       (xhr/send url do-callback method payload headers))))

(defn get [url callback]
  (request url callback))

(defn post
  ([url]
     (post url nil nil))
  ([url callback]
     (post url callback nil))
  ([url callback payload]
     (request url callback "POST" payload {:Content-Type "application/json"})))

(defn put
  ([url]
     (put url nil nil))
  ([url callback]
     (put url callback nil))
  ([url callback payload]
     (request url callback "PUT" payload {:Content-Type "application/json"})))

(defn delete
  ([url]
     (delete url nil))
  ([url callback]
     (request url callback "DELETE")))