package sumsTests;

import sums.Addition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class additionTest {
    @Test
    public void testAdd() {
        Addition addition = new Addition();
        assertEquals(5, addition.add(2, 3), "2 + 3 should equal 5");
        assertEquals(0, addition.add(-2, 1), "-2 + 1 should equal 0");
        assertEquals(0, addition.add(0, 0), "0 + 0 should equal 0");

        System.out.println("All tests passed!");
    }
}
