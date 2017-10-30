import java.awt.*;

/**
 * Created by Raffaele on 05/10/2017.
 */
public class MouseInteraction {
    public Robot r;
    private int oldX;
    private int oldY;
    private int movTime;
    private int movX;
    private int movY;
    private int newX;
    private int newY;
    int i=1;
    public MouseInteraction () throws AWTException {
        r = new Robot();
        oldX = 0;
        oldY = 0;
    }

    public void moveTo (int offsetX, int offsetY, int time) throws InterruptedException {
        if (offsetX != 0) movTime = time / Math.abs(offsetX);
        else if (offsetY != 0) movTime = time / Math.abs(offsetY);
        newX = oldX + offsetX;
        newY = oldY + offsetY;
        if      (offsetX > 0)  movX = 1;
        else if (offsetX == 0) movX = 0;
        else                   movX = -1;
        if      (offsetY > 0)  movY = 1;
        else if (offsetY == 0) movY = 0;
        else                   movY = -1;

        while (oldX != newX || oldY != newY) {
            Thread.sleep(movTime);
            r.mouseMove(oldX, oldY);
            oldX +=movX;
            oldY +=movY;
        }
    }
}
