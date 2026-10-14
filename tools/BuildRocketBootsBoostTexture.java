import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Creates the rocket-boots exhaust by changing only the jetpack plume's hue to warm yellow. */
public final class BuildRocketBootsBoostTexture {
    private static final float YELLOW_HUE = 0.14F;

    private BuildRocketBootsBoostTexture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "usage: BuildRocketBootsBoostTexture <jetpack-boost.png> <output.png>");
        }

        BufferedImage input = ImageIO.read(new File(args[0]));
        BufferedImage output = new BufferedImage(
                input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_ARGB);
        float[] hsb = new float[3];
        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                int argb = input.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    output.setRGB(x, y, 0);
                    continue;
                }
                int red = argb >> 16 & 0xFF;
                int green = argb >> 8 & 0xFF;
                int blue = argb & 0xFF;
                Color.RGBtoHSB(red, green, blue, hsb);
                int yellowRgb = Color.HSBtoRGB(YELLOW_HUE, hsb[1], hsb[2]);
                // Alpha and therefore the complete particle silhouette remain bit-for-bit identical.
                output.setRGB(x, y, alpha << 24 | yellowRgb & 0x00FFFFFF);
            }
        }

        File outputFile = new File(args[1]);
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("could not create " + parent);
        }
        if (!ImageIO.write(output, "png", outputFile)) {
            throw new IllegalStateException("no PNG writer available");
        }
    }
}
