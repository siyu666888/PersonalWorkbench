import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 点击指定坐标后截图（用于逐页验证桌面版各界面渲染）。
 * 用法: java ClickShot.java <x> <y> <输出png> [等待毫秒]
 */
public class ClickShot {
    public static void main(String[] args) throws Exception {
        int x = Integer.parseInt(args[0]);
        int y = Integer.parseInt(args[1]);
        String out = args[2];
        long wait = args.length > 3 ? Long.parseLong(args[3]) : 1500;

        Robot robot = new Robot();
        robot.mouseMove(x, y);
        robot.delay(300);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(80);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(wait);

        Rectangle r = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage img = robot.createScreenCapture(r);
        File f = new File(out);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("clicked(" + x + "," + y + ") -> " + out);
    }
}
