(ns clojure.core.matrix.impl.ndarray
  (:require [clojure.core.matrix.protocols :as mp])
  (:use clojure.core.matrix.utils)
  (:require [clojure.core.matrix.implementations :as imp])
  (:require [clojure.core.matrix.multimethods :as mm])
  (:refer-clojure :exclude [vector?]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* true)

;; Lightweight support for n-dimensional arrays of arbitrary objects conforming to clojure.core.matrix API 
;; 1D / 2D arrays of java.lang.Number can be safely used as vectors and matrices respectively

;; utility functions

(defn calc-total-size 
  (^long [^longs dims]
    (let [c (count dims)]
      (loop [i (long 0) result (long 1)]
        (if (>= i c)
          result
          (recur (inc i) (* result (aget dims i))))))))

(defn calc-index
  (^long [indexes ^longs shape]
    (areduce shape i result 0 
                           (+ (long (nth indexes i)) 
                              (* result (aget shape i)))))) 

;; =======================================================
;; N-dimensional array object

(deftype NDArray 
  [^objects data
   ^longs shape]
  mp/PImplementation
    (implementation-key [m] :ndarray)
    (new-vector [m length] 
      (NDArray. (object-array length) (long-array-of length)))
    (new-matrix [m rows columns] 
      (let [rows (long rows)
            columns (long columns)]
        (NDArray. (object-array (* rows columns)) (long-array-of rows columns))))
    (new-matrix-nd [m dims] 
      (let [^longs shape (apply long-array-of dims)
            size (calc-total-size shape)]
        (NDArray. (object-array size) shape)))
    (construct-matrix [m data] 
      (let [^longs shape (long-array (mp/get-shape data))
            size (calc-total-size shape)
            result (NDArray. (object-array size) shape)]
        (mp/assign! result data)
        result))
    (supports-dimensionality? [m dims] 
      true)
  
  mp/PIndexedAccess
    (get-1d [m x]
      (aget data x))
    (get-2d [m x y]
      (let [stride (long (aget shape 1))]
        (aget data (+ (long y) (* stride (long x))))))
    (get-nd [m indexes]
      (let [ndims (count shape)
            index (calc-index indexes shape)]
        (aget data index))) 
    
  mp/PIndexedSetting
    (set-1d [m row v]
      (let [m (mp/clone m)]
        (mp/set-1d! m row v)
        m))
    (set-2d [m row column v]
      (let [m (mp/clone m)]
        (mp/set-2d! m row column v)
        m))
    (set-nd [m indexes v]
      (let [m (mp/clone m)]
        (mp/set-nd! m indexes v)
        m))
    (is-mutable? [m]
      true)
    
  mp/PDimensionInfo
    (get-shape [m]
      shape)
    (is-vector? [m]
      (== 1 (count shape))) 
    (is-scalar? [m]
      false) ;; TODO: what about zero dimension case??
    (dimensionality [m]
      (count shape))
    (dimension-count [m x]
      (aget shape x))
    
  mp/PConversion
    (convert-to-nested-vectors [m]
      (if (== 1 (alength shape))
        (into [] data)
        (mapv mp/convert-to-nested-vectors (mp/get-major-slice-seq m))))
    
  mp/PIndexedSettingMutable
    (set-1d! [m x v]
      (aset data x v))
    (set-2d! [m x y v]
      (let [stride (long (aget shape 1))]
        (aset data (+ (long y) (* stride (long x))) v)))
    (set-nd! [m indexes v]
      (let [ndims (count shape)
            index (calc-index indexes shape)]
        (aset data index v)))
    
    ;; TODO: implementations of other protocols for ND arrays
    )

(defn make-ndarray [shape]
  "Construct an NDArray with the specified dimensions. All values are initially null."
  (let [^longs shape (long-array shape)
        asize (areduce shape i result 1 (* result (aget shape i)))]
    (NDArray. (object-array asize)
              shape)))

;; =====================================
;; Register implementation

(imp/register-implementation (make-ndarray [1]))