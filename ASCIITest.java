package smc.ext;

/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class ASCIITest {
    public ASCIITest() {
    }

    public static void main(String[] args) {
        for (int i = 0; i < 255; i++) {
            char c = (char)i;
            System.out.println(c);
        }
    }
}
