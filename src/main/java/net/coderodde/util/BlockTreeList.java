package net.coderodde.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Queue;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

//! (Apr 1, 2019) Experiment with block list chain.
/**
 * This AVL-tree based list structure implements an ordered sequence or, in 
 * other words, a list data structure. The idea behind this data structure is 
 * that all non-bulk operations run in worst-case logarithmic time.
 * 
 * @author Rodion "rodde" Efremov
 * @version 1.6 
 */
public class BlockTreeList<E> implements List<E>, 
                                         Deque<E>,
                                         Queue<E>,
                                         Serializable {

    /**
     * The minimum allowed (and possible) capacity of each block node.
     */
    private static final int MINIMUM_BLOCK_NODE_CAPACITY = 1;
    
    /**
     * The default capacity of each node block.
     */
    private static final int DEFAULT_BLOCK_NODE_CAPACITY = 25;
    
    /**
     * The minimum value for a requested load factor. 
     */
    private static final float MINIMUM_REQUESTED_LOAD_FACTOR = 0.01f;
    
    /**
     * The maximum value for a requested load factor.
     */
    private static final float MAXIMUM_REQUESTED_LOAD_FACTOR = 0.5f;
    
    /**
     * The default value for a requested load factor.
     */
    private static final float DEFAULT_REQUESTED_LOAD_FACTOR = 0.3f;
    
    /**
     * This static inner class implements a tree node.
     * 
     * @param <E> the element type.
     */
    private static final class TreeListBlockNode<E> {
        
        /**
         * The actual element array of this block node.
         */
        E[] array;
        
        /**
         * Number of elements currently in the block node.
         */
        int size;
        
        /**
         * The physical index of the element that is logically first (has 
         * logical index 0).
         */
        int headIndex;
        
        /**
         * The left node of this block node.
         */
        TreeListBlockNode<E> left;
        
        /**
         * The right node of this block node.
         */
        TreeListBlockNode<E> right;
        
        /**
         * The parent node of this block node.
         */
        TreeListBlockNode<E> parent;
        
        /**
         * The previous block node of this node.
         */
        TreeListBlockNode<E> prev;
        
        /**
         * The next block node of this node.
         */
        TreeListBlockNode<E> next;
        
        /**
         * The height of this node in the entire tree. Leaves have height of 
         * zero (0).
         */
        int height;
        
        /**
         * The number of nodes in the left subtree.
         */
        int leftCount;
        
        TreeListBlockNode(int capacity) {
            this.array = (E[]) new Object[capacity];
        }
        
        /**
         * Returns {@code true} only if this block is full.
         * 
         * @return {@code true} if this block is full.
         */
        boolean isFull() {
            return size == array.length;
        }
        
        /**
         * Appends the input element to this block.
         * 
         * @param element the element to append.
         */
        void append(E element) {
            array[convertLogicalIndexToPhysicalIndex(size++)] = element;
        }
        
        /**
         * Prepends the input element to this block.
         * 
         * @param element the element to prepend.
         */
        void prepend(E element) {
            array[headIndex = (headIndex == 0 ?
                  array.length - 1:
                  headIndex - 1)] = element;
            size++;
        }
        
        /**
         * Converts the logical index to physical index.
         * 
         * @param logicalIndex the logical index.
         * @return the physical index.
         */
        int convertLogicalIndexToPhysicalIndex(int logicalIndex) {
            return (headIndex + logicalIndex) % array.length;
        }
        
        E get(int logicalIndex) {
            return array[convertLogicalIndexToPhysicalIndex(logicalIndex)];
        }
        
        /**
         * Sets the specified element and returns the old element in the same
         * array component.
         * 
         * @param logicalIndex the logical index of the array component.
         * @param element      the element to set.
         * @return             the old element.
         */
        E set(int logicalIndex, E element) {
            int index = convertLogicalIndexToPhysicalIndex(logicalIndex);
            E oldElement = array[index];
            array[index] = element;
            return oldElement;
        }
    }
    
    /**
     * The root node of this tree.
     */
    private transient TreeListBlockNode<E> root;
    
    /**
     * The very first tree list block node.
     */
    private transient TreeListBlockNode<E> head;
    
    /**
     * The very last tree list block node.
     */
    private transient TreeListBlockNode<E> tail;
    
    /**
     * The number of elements in this list. Counts also elements that are 
     * {@code null].
     * 
     * @serial
     */
    private int size;
    
    /**
     * This field stores the number of times this list was structurally 
     * modified.
     */
    private transient int modificationCount = 0;
    
    /**
     * The minimum load factor. Should be in the range {@code (0, 1)} (open 
     * range).
     */
    private final float minimumAllowedLoadFactor;
    
    /**
     * The capacity of all the block nodes.
     */
    private final int blockNodeCapacity;
    
    /**
     * Holds the number of blocks in this tree list.
     */
    private int blocks;
    
    /**
     * Constructs an empty tree list with default block capacity and minimum 
     * load factor.
     */
    public BlockTreeList() {
        this(DEFAULT_BLOCK_NODE_CAPACITY);
    }
    
    /**
     * Constructs an empty tree list whose block nodes have capacity
     * {@code requestedBlockNodeCapacity}.
     * 
     * @param requestedBlockNodeCapacity the requested block node capacity.
     */
    public BlockTreeList(int requestedBlockNodeCapacity) {
        this(requestedBlockNodeCapacity, DEFAULT_REQUESTED_LOAD_FACTOR);
    }
    
    /**
     * Constructs an empty tree list with default block capacity and the given
     * requested minimum load factor.
     * 
     * @param requestedMinimumLoadFactor the requested minimum load factor.
     */
    public BlockTreeList(float requestedMinimumLoadFactor) {
        this(DEFAULT_BLOCK_NODE_CAPACITY, requestedMinimumLoadFactor);
    }
    
    /**
     * Constructs a tree list containing the data in {@code collection}.
     * 
     * @param collection the collection to initialize this tree list with.
     */
    public BlockTreeList(Collection<? extends E> collection) {
        this(collection,
             DEFAULT_BLOCK_NODE_CAPACITY,
             DEFAULT_REQUESTED_LOAD_FACTOR);
    }
    
    /**
     * Constructs a tree list with a given requested block node capacity and 
     * initialized with the data in {@code collection}.
     * 
     * @param requestedBlockNodeCapacity the requested block node capacity.
     * @param collection                 the collection to initialize the tree
     *                                   list with.
     */
    public BlockTreeList(int requestedBlockNodeCapacity,
                         Collection<? extends E> collection) {
        this(collection, 
             requestedBlockNodeCapacity, 
             DEFAULT_REQUESTED_LOAD_FACTOR);
    }
    
    /**
     * Constructs a tree list with a given requested minimum load factor and
     * initialized with the data in {@code collection}. 
     * 
     * @param requesteddMinimumLoadFactor the requested minimum load factor.
     * @param collection                  the collection to initialize the tree
     *                                    list with.
     */
    public BlockTreeList(float requesteddMinimumLoadFactor,
                         Collection<? extends E> collection) {
        this(collection,
             DEFAULT_BLOCK_NODE_CAPACITY,
             requesteddMinimumLoadFactor);
    }
    
    /**
     * Constructs an empty tree list with a given requested block node capacity
     * and a given requested minimum load factor.
     * 
     * @param requestedBlockNodeCapacity the requested block node capacity.
     * @param requestedMinimumLoadFactor the requested minimum load factor.
     */
    public BlockTreeList(int requestedBlockNodeCapacity,
                         float requestedMinimumLoadFactor) {
        this.blockNodeCapacity = 
                fixBlockNodeCapacity(requestedBlockNodeCapacity);
        
        this.minimumAllowedLoadFactor = 
                fixRequestedLoadFactor(requestedMinimumLoadFactor);
    }
    
    /**
     * Constructs a tree list with a given requested block node capacity,
     * requested minimum load factor, and data to initialize the list with.
     * 
     * @param collection                 the collection to initialize the tree
     *                                   list with.
     * @param requestedBlockNodeCapacity the requested block node capacity.
     * @param requestedMinimumLoadFactor the requested minimum load capacity.
     */
    public BlockTreeList(Collection<? extends E> collection,
                         int requestedBlockNodeCapacity,
                         float requestedMinimumLoadFactor) {
        this.blockNodeCapacity = 
                fixBlockNodeCapacity(requestedBlockNodeCapacity);
        
        this.minimumAllowedLoadFactor = 
                fixRequestedLoadFactor(requestedMinimumLoadFactor);
        
        //! Add the collection to the list.
        if (collection == null) {
            throw new NullPointerException("The input collection is null.");
        }
        
    }
    
    @Override
    public void addFirst(E e) {
        if (root == null) {
            root = new TreeListBlockNode<>(blockNodeCapacity);
            head = root;
            tail = root;
            root.append(e);
            blocks = 1;
        } else if (head.isFull()) {
            TreeListBlockNode<E> newNode = 
                    new TreeListBlockNode<>(blockNodeCapacity);
            
            newNode.append(e);
            newNode.parent = head;
            head.left = newNode;
            head.prev = newNode;
            newNode.next = head;
            head = newNode;
            blocks++;
            
            // Now restore the AVL-tree invariants:
            updateLeftCounts(newNode, 1);
            fixAfterInsertion(newNode);
        } else {
            head.prepend(e);
            updateLeftCounts(head, 1);
        }
        
        size++;
        modificationCount++;
    }

    @Override
    public void addLast(E e) {
        if (root == null) {
            root = new TreeListBlockNode<>(blockNodeCapacity);
            head = root;
            tail = root;
            blocks = 1;
        } else if (tail.isFull()) {
            TreeListBlockNode<E> newNode =
                    new TreeListBlockNode<>(blockNodeCapacity);

            newNode.append(e);
            newNode.parent = tail;
            tail.right = newNode;
            tail.next = newNode;
            newNode.prev = tail;
            tail = newNode;
            blocks++;
           
            // Now restore the AVL-tree invariant:
            updateLeftCounts(newNode.parent, 1);
            fixAfterInsertion(newNode);
        } else {
            tail.append(e);
        }
        
        size++;
        modificationCount++;
    }
    
    private void updateLeftCounts(TreeListBlockNode<E> startNode, int delta) {
        TreeListBlockNode<E> node = startNode;
        TreeListBlockNode<E> parent = node.parent;
        
        while (parent != null) {
            if (parent.left == node) {
                parent.leftCount += delta;
            }
            
            node = parent;
            parent = parent.parent;
        }
    }

    @Override
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    @Override
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    @Override
    public E removeFirst() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E removeLast() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E pollFirst() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E pollLast() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E getFirst() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E getLast() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E peekFirst() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E peekLast() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean offer(E e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E remove() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E poll() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E element() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E peek() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void push(E e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E pop() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Iterator<E> descendingIterator() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean add(E e) {
        addLast(e);
        return true;
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        List.super.replaceAll(operator); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void sort(Comparator<? super E> c) {
        List.super.sort(c); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E get(int index) {
        accessRangeCheck(index);
        TreeListBlockNode<E> node = root;
        
        while (true) {
            if (index >= node.leftCount + node.size) {
                index -= node.leftCount + node.size;
                node = node.right;
            } else if (index < node.leftCount) {
                node = node.left;
            } else {
                return node.get(index - node.leftCount);
            }
        }
    }

    @Override
    public E set(int index, E element) {
        accessRangeCheck(index);
        TreeListBlockNode<E> node = root;
        
        while (true) {
            if (index >= node.leftCount + node.size) {
                index -= node.leftCount + node.size;
                node = node.right;
            } else if (index < node.leftCount) {
                node = node.left;
            } else {
                return node.set(index - node.leftCount, element);
            }
        }
    }

    @Override
    public void add(int index, E element) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public E remove(int index) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int indexOf(Object o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ListIterator<E> listIterator() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Spliterator<E> spliterator() {
        return List.super.spliterator(); //To change body of generated methods, choose Tools | Templates.
    }
//
//    @Override
//    public <T> T[] toArray(IntFunction<T[]> generator) {
//        return null;
//    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        return List.super.removeIf(filter); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Stream<E> stream() {
        return List.super.stream(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Stream<E> parallelStream() {
        return List.super.parallelStream(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        List.super.forEach(action); //To change body of generated methods, choose Tools | Templates.
    }
    
    public static void main(String[] args) {
        for (int i = 10; i >= -10; i--) {
            System.out.println(i + " -> " + Math.floorMod(i, 3));
        }
    }
    
    private static final <E> int height(TreeListBlockNode<E> node) {
        return node != null ? node.height : -1;
    }
    
    private static final <E> 
    TreeListBlockNode<E> leftRotate(TreeListBlockNode<E> node1) {
        TreeListBlockNode<E> node2 = node1.right;
        node2.parent = node1.parent;
        node1.parent = node2;
        node1.right = node2.left;
        node2.left = node1;
        
        if (node1.right != null) {
            node1.right.parent = node1;
        }
        
        node1.height = Math.max(height(node1.left),
                                height(node1.right)) + 1;
        
        node2.height = Math.max(height(node2.left), 
                                height(node2.right)) + 1;
        
        node2.leftCount += node1.leftCount + node1.size;
        return node2;
    }
    
    private static final <E> 
    TreeListBlockNode<E> rightRotate(TreeListBlockNode<E> node1) {
        TreeListBlockNode<E> node2 = node1.left;
        node2.parent = node1.parent;
        node1.parent = node2;
        node1.left = node2.right;
        node2.right = node1;
        
        if (node1.left != null) {
            node1.left.parent = node1;
        }
        
        node1.height = Math.max(height(node1.left),
                                height(node1.right)) + 1;
        
        node2.height = Math.max(height(node2.left),
                                height(node2.right)) + 1;
        
        node1.leftCount -= (node2.leftCount + node2.size);
        return node2;
    }
        
    private static final <E>
    TreeListBlockNode<E> rightLeftRotate(TreeListBlockNode<E> node1) {
        TreeListBlockNode<E> node2 = node1.right;
        node1.right = rightRotate(node2);
        return leftRotate(node1);
    }
    
    private static final <E>
    TreeListBlockNode<E> leftRightRotate(TreeListBlockNode<E> node1) {
        TreeListBlockNode<E> node2 = node1.left;
        node1.left = leftRotate(node2);
        return rightRotate(node1);
    }
    
    private final void fixAfterInsertion(TreeListBlockNode<E> node) {
        TreeListBlockNode<E> parent = node.parent;
        TreeListBlockNode<E> grandParent;
        TreeListBlockNode<E> subTreeRoot;
        
        while (parent != null) {
            if (height(parent.left) == height(parent.right) + 2) {
                grandParent = parent.parent;
                
                if (height(parent.left.left) >= height(parent.left.right)) {
                    subTreeRoot = rightRotate(parent);
                } else {
                    subTreeRoot = leftRightRotate(parent);
                }
                
                if (grandParent == null) {
                    root = subTreeRoot;
                } else if (grandParent.left == parent) {
                    grandParent.left = subTreeRoot;
                } else {
                    grandParent.right = subTreeRoot;
                }
                
                if (grandParent != null) {
                    grandParent.height = 
                            Math.max(height(grandParent.left),
                                     height(grandParent.right)) + 1;
                }
                
                return;
            } else if (height(parent.right) == height(parent.left) + 2) {
                grandParent = parent.parent;
                
                if (height(parent.right.right) >= height(parent.right.left)) {
                    subTreeRoot = leftRotate(parent);
                } else {
                    subTreeRoot = rightLeftRotate(parent);
                }
                
                if (grandParent == null) {
                    root = subTreeRoot;
                } else if (grandParent.left == parent) {
                    grandParent.left = subTreeRoot;
                } else {
                    grandParent.right = subTreeRoot;
                }
                
                if (grandParent != null) {
                    grandParent.height = 
                            Math.max(height(grandParent.left),
                                     height(grandParent.right)) + 1;
                }
                
                return;
            } 
            
            parent.height  = Math.max(height(parent.left), 
                                      height(parent.right)) + 1;
            parent = parent.parent;
        }
    }
    
    /**
     * Fixes the {@code requestedLoadFactor} such that it's new value is between
     * {@code MINIMUM_REQUESTED_LOAD_FACTOR} and 
     * {@code MAXIMUM_REQUESTED_LOAD_FACTOR}.
     * 
     * @param requestedLoadFactor the requested load factor to fix.
     * @return fixed requested load factor.
     */
    private static final float
         fixRequestedLoadFactor(float requestedLoadFactor) {
        return Math.min(MAXIMUM_REQUESTED_LOAD_FACTOR, 
                        Math.max(MINIMUM_REQUESTED_LOAD_FACTOR, 
                                 requestedLoadFactor));
    }
         
    private static final int fixBlockNodeCapacity(
            int requestedNodeBlockCapacity) {
        return Math.max(requestedNodeBlockCapacity,
                        MINIMUM_BLOCK_NODE_CAPACITY);
    }
         
    private boolean shouldCompact() {
        int currentCapacity = size;
        int maximumCapacity = blocks * blockNodeCapacity;
        
        return ((float) currentCapacity) / maximumCapacity 
                < minimumAllowedLoadFactor;
    }
    
    private void accessRangeCheck(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException(
                    accessRangeCheckOutOfBoundsMessage(index));
        }
    }
    
    private String accessRangeCheckOutOfBoundsMessage(int index) {
        return String.format("Index: {0}, Size: {1}", index, size);
    }
}
