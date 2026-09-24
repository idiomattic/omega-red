(ns omega-red.codec-test
  (:require [clojure.test :refer [deftest testing is]]
            [omega-red.codec :as codec])
  (:import [redis.clients.jedis.util KeyValue]))

(deftest key-serialization-test
  (testing "this will blow up"
    (is (thrown-with-msg? Throwable #"Assert failed" (codec/serialize-key [nil]))))

  (is (= "a-key" (codec/serialize-key ["a-key"])))

  (is (= "x:one" (codec/serialize-key [:x "one"])))
  (is (= "omega-red.codec-test/x:one:two" (codec/serialize-key [::x "one" "two"]))))

(deftest data-de-serialization-test
  (testing "round trip for Clojure data"
    (is (= #{:x}
           (-> ;; input to Jedis is always a string
            (codec/serialize #{:x})
            ;; this is because internally, Jedis always gives us byte arrays
            (String/.getBytes "UTF-8")
            codec/deserialize))))

  (testing "simple data types - preserves Jedis/Redis return value semantics (aka losing type info)"
    (is (= "1" (-> (codec/serialize 1)
                   codec/deserialize)))

    (is (= "true" (-> (codec/serialize true)
                      codec/deserialize))))

  (testing "special case: things like HSET or SET return a number"
    (is (= 1 (codec/deserialize 1)))))

(defn- ->bytes [s]
  (String/.getBytes ^String s "UTF-8"))

(defn- array-list [& items]
  (java.util.ArrayList. ^java.util.Collection (vec items)))

(deftest resp3-map-reply-deserialization-test
  (testing "a list of KeyValue pairs (RESP3 map reply, e.g. HGETALL) flattens to [field val field val ...]"
    (is (= ["a" "1" "b" "2"]
           (codec/deserialize (array-list (KeyValue. (->bytes "a") (->bytes "1"))
                                          (KeyValue. (->bytes "b") (->bytes "2")))))))

  (testing "KeyValue values holding Clojure data are unserialized"
    (is (= ["a" {:foo #{1 2}}]
           (codec/deserialize (array-list (KeyValue. (->bytes "a") (->bytes (codec/serialize {:foo #{1 2}}))))))))

  (testing "lists without KeyValue items keep their shape"
    (is (= ["x" ["y" "z"]]
           (codec/deserialize (array-list (->bytes "x")
                                          (array-list (->bytes "y") (->bytes "z")))))))

  (testing "an empty list stays an empty vector"
    (is (= [] (codec/deserialize (array-list))))))
