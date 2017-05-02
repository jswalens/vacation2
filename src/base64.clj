(ns base64
  (:import java.util.Base64))

(defn str->base64 [data]
  "Base64-encode the string `data`, to a string."
  (.encodeToString (Base64/getEncoder) (.getBytes data)))

(defn base64->str [data]
  "Base64-decode the string `data`, to a string."
  (String. (.decode (Base64/getDecoder) data)))
