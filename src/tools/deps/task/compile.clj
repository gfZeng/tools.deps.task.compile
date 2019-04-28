(ns tools.deps.task.compile
  (:import [javax.tools ToolProvider]
           [java.net URL URLClassLoader])
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.main]))


(def ^:dynamic *target-dir*
  (or (System/getProperty "aot.target")
      (System/getenv "AOT_TARGET")
      "target"))


(defn class-file-outdated? [dirname file]
  (let [clazz (-> (.getPath file)
                  (str/replace dirname "")
                  (str/replace #"\.java$" ".class")
                  (->> (str *target-dir*))
                  (io/file))]
    (<= (.lastModified clazz) (.lastModified file))))

(defn updated-java-files [dir]
  (let [dirname (.getPath dir)]
    (->> (file-seq dir)
         (filter #(str/ends-with? (.getName %) ".java"))
         (filter #(class-file-outdated? dirname %)))))

(defn javac [dirs]
  (let [files    (mapcat updated-java-files dirs)
        compiler (ToolProvider/getSystemJavaCompiler)
        target   (io/file *target-dir*)]
    (when-not (.exists target)
      (.mkdir target))
    (when-some [filenames (seq (map #(.getPath %) files))]
      (->> filenames
           (concat ["-d" (.getPath target)])
           (into-array String)
           (.run compiler nil nil nil)))
    (let [loader (.getContextClassLoader (Thread/currentThread))
          method (.getDeclaredMethod
                  URLClassLoader "addURL"
                  (into-array Class [URL]))]
      (.setAccessible method true)
      (.invoke method loader (to-array [(io/as-url target)])))))

(defn aot [dirs]
  (require 'clojure.tools.namespace.find)
  (let [f    (ns-resolve 'clojure.tools.namespace.find
                         'find-namespaces-in-dir)
        nses (remove #(:skip-aot? (meta %)) (mapcat f dirs))]
    (binding [*compile-path* *target-dir*]
      (run! compile nses))))

(defn -main [& args]
  (let [dirs (->> (str/split (System/getProperty "java.class.path") #":")
                  (map io/file)
                  (filter #(.isDirectory %)))
        aot? (not (.exists (io/file *target-dir*)))]
    (.setContextClassLoader
     (Thread/currentThread)
     (clojure.lang.RT/makeClassLoader))
    (javac dirs)
    (when aot? (aot dirs)))
  (if (and (= (first args) "-m")
           (str/includes? (second args) "/"))
    (let [[ns method] (map symbol (str/split (second args) #"/"))]
      (require ns)
      (apply (ns-resolve ns method) (drop 2 args)))
    (apply clojure.main/main args)))

