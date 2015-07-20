(ns quanta.merkle
  "A linear Merkle tree over sorted key-values.")

(defrecord Node [^long hash min-key max-key left right])

(defn node=
  "Returns true if node-a and node-b are equal, otherwise false."
  [^Node node-a ^Node node-b]
  (if (nil? node-a)
    (nil? node-b)
    (and node-b
         (= (:hash node-a) (:hash node-b))
         (= (:min-key node-a) (:min-key node-b))
         (= (:max-key node-a) (:max-key node-b)))))

(defn key-range
  "Given a node, returns the key range of a given node."
  [^Node node]
  (list (:min-key node) (:max-key node)))

(defn children
  "Given a node, returns a sequence of a node's children."
  [^Node node]
  (list (:left node) (:right node)))

(defn leaf?
  "Returns true if the given node is a leaf node, i.e. has no left or right
  children, false otherwise."
  [^Node node]
  (if (nil? node)
    false
    (every? nil? (children node))))

(defn join-nodes
  "Given two nodes, returns a new node with the min-key from left and the
  max-key from right as well as the combined hash of left and right. If either
  left or right is nil, returns right or left, respectively."
  [^Node left ^Node right]
  (cond
    (nil? left)  right
    (nil? right) left
    :else (Node. (hash (list (:hash left) (:hash right)))
                 (:min-key left)
                 (:max-key right)
                 left
                 right)))

(defn populate-levels
  "Returns a vector of subtrees where the vector's index represents the tree's
  height. This works by putting nodes, one at a time, onto the 0th index of the
  vector, recursively merging and moving them to index i + 1 when an occupied
  index is encountered. The result is a vector of subtrees which may then be
  merged into a single tree."
  [levels i ^Node right]
  (let [left (nth levels i :grow)]
    (condp = left
      nil   (assoc! levels i right)
      :grow (conj! levels right)
      (recur (assoc! levels i nil) (inc i) (join-nodes left right)))))

(defn merge-levels
  "Given a vector of subtrees, merges those trees together by reducing them via
  join-nodes. Returns a single merged tree."
  [levels]
  (let [levels (persistent! levels)]
    (when-not (empty? levels)
      (->> levels reverse (remove nil?) (reduce join-nodes)))))

(defn tree
  "Given a collection of sorted key-value forms, returns a new Merkle tree."
  [coll]
  (->> coll
       (reduce (fn [levels [k v]]
                 (let [h (hash v)
                       n (Node. h k k nil nil)]
                   (populate-levels levels 0 n)))
               (transient []))
       merge-levels))

(defn diff
  "Given two Merkle trees, returns a lazy sequence of their differing key
  ranges. Specifically these differences are formed as vectors of where the
  first element is the first tree's node and the second element the second
  trees's."
  [root-a root-b]
  (when-not (node= root-a root-b)
    (lazy-cat
      ;; Traverse the left side of the tree.
      (when-not (node= (:left root-a) (:left root-b))
        (diff (:left root-a) (:left root-b)))

      ;; Return leaf key ranges as "tuples" where the first element is the
      ;; right leaf and the second the left.
      (cond
        (and (leaf? root-a) (leaf? root-b))
        [[(key-range root-a) (key-range root-b)]]

        (leaf? root-a)
        [[(key-range root-a) nil]]

        (leaf? root-b)
        [[nil (key-range root-b)]])

      ;; Traverse the right side of the tree.
      (when-not (node= (:right root-a) (:right root-b))
        (diff (:right root-a) (:right root-b))))))
