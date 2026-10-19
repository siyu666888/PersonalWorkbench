import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** 屏幕截图（验证桌面版 GUI 是否正常渲染） */
public class Shot {
    public static void main(String[] args) throws Exception {
        Rectangle r = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage img = new Robot().createScreenCapture(r);
        File out = new File(args[0]);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("saved: " + out.getAbsolutePath() + " " + img.getWidth() + "x" + img.getHeight());
    }
}
