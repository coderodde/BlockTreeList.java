package net.coderodde.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author rodde
 */
public class BlockTreeListTest {
    
    private BlockTreeList<Integer> treeList;
    
    @Test
    public void testGetAndSet() {
        for (int blockCapacity = 1; blockCapacity <= 13; blockCapacity++) {
            treeList = new BlockTreeList<>(blockCapacity);

            for (int i = 4; i >= 0; i--) {
                assertEquals(4 - i, treeList.size());
                treeList.addFirst(i);
                assertEquals(5 - i, treeList.size());
            }

            for (int i = 5; i < 10; i++) {
                assertEquals(i, treeList.size());
                treeList.addLast(i);
                assertEquals(i + 1, treeList.size());
            }

            System.out.println("");

            for (int i = 0; i < 10; i++) {
                assertEquals((Integer) i, treeList.get(i));
                treeList.set(i, i + 10);
            }

            for (int i = 0; i < 10; i++) {
                assertEquals((Integer)(i + 10), treeList.get(i));
            }
        }
    }
}
