(ns tools.deps.task.compile
  (:import [javax.tools ToolProvider]
           [java.net URL URLClassLoader])
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.main]
            [clojure.tools.namespace.find :as nsfind]))


(def ^:dynamic *target-dir* "target")


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
      (println "********** compiling **********")
      (run! println filenames)
      (->> filenames
           (concat ["-d" (.getPath target)])
           (into-array String)
           (.run compiler nil nil nil))
      (println "********** compiled  **********\n"))
    (let [loader (ClassLoader/getSystemClassLoader)
          method (.getDeclaredMethod
                  URLClassLoader "addURL"
                  (into-array Class [URL]))]
      (.setAccessible method true)
      (.invoke method loader (to-array [(io/as-url target)])))))

(defn aot [dirs]
  (let [nses (mapcat nsfind/find-namespaces-in-dir dirs)]
    (binding [*compile-path* *target-dir*]
      (run! compile nses))))

(defn -main [& args]
  (let [dirs   (->> (str/split (System/getProperty "java.class.path") #":")
                    (map io/file)
                    (filter #(.isDirectory %)))
        aoted? (.exists (io/file *target-dir*))]
    (javac dirs)
    (if aoted?
      (println "Remove" *target-dir*  "directory if you want AOT forced")
      (aot dirs)))
  (apply clojure.main/main args))

