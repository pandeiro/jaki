(ns jaki.req
  (:use [jaki.util :only [clj->js]])
  (:require [goog.net.XhrIo :as xhr]
            [goog.json :as json])
  (:refer-clojure :exclude [get]))

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
     (let [do-callback (if (fn? callback)
                         (fn [e] (callback (js->clj (. e/target (getResponseJson))
                                                    :keywordize-keys true)))
                         nil)
           payload (if (or (string? payload) (number? payload) (nil? payload)) payload
                       (json/serialize (if (map? payload) (clj->js payload) payload)))
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