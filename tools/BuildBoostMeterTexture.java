import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Builds the layered pixel-art texture used by the jetpack's half-ring boost meter.
 *
 * <p>The output contains an empty industrial frame, thirteen cumulative fill states, and a full
 * red overheat flash in vertically stacked 112x58 layers. The discrete states let the HUD fill
 * whole angled segments instead of clipping through them with a rectangular reveal.</p>
 */
public final class BuildBoostMeterTexture {
    private static final int WIDTH = 112;
    private static final int LAYER_HEIGHT = 58;
    private static final int SEGMENTS = 13;
    private static final int LAYERS = SEGMENTS + 2;

    private static final int CENTER_X = WIDTH / 2;
    private static final int CENTER_Y = 44;
    private static final int OUTER_RADIUS = 40;
    private static final int INNER_RADIUS = 28;
    private static final double START_ANGLE = 174.0;
    private static final double END_ANGLE = 6.0;
    private static final double SEGMENT_GAP = 2.35;

    private static final Color[] NORMAL_COLORS = {
            color(0xE8C45B), color(0xEDC65B), color(0xF4D26E), color(0xF7D471),
            color(0xFAD56E), color(0xFFD976), color(0xFFD169), color(0xFFC45B),
            color(0xFFB43C), color(0xFFA42C), color(0xFF981F), color(0xF08A24),
            color(0xFFD976)
    };

    private static final Color[] OVERHEAT_COLORS = {
            color(0xFF5A35), color(0xFF5331), color(0xFF4C2E), color(0xFF452B),
            color(0xFF3E28), color(0xFF3725), color(0xFF3223), color(0xFB2D20),
            color(0xF4291E), color(0xEC251C), color(0xE6221B), color(0xDF201A),
            color(0xD91D18)
    };

    private BuildBoostMeterTexture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: BuildBoostMeterTexture <output.png>");
        }

        BufferedImage texture = new BufferedImage(WIDTH, LAYER_HEIGHT * LAYERS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = texture.createGraphics();
        configurePixelArt(graphics);

        drawEmptyFrame(graphics, 0);
        for (int filledSegments = 1; filledSegments <= SEGMENTS; filledSegments++) {
            drawFillLayer(graphics, LAYER_HEIGHT * filledSegments, NORMAL_COLORS,
                    filledSegments, false);
        }
        drawFillLayer(graphics, LAYER_HEIGHT * (SEGMENTS + 1), OVERHEAT_COLORS,
                SEGMENTS, true);
        graphics.dispose();

        File output = new File(args[0]);
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("could not create " + parent);
        }
        writePng(texture, output);
    }

    private static void configurePixelArt(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static void drawEmptyFrame(Graphics2D graphics, int offsetY) {
        // Quiet black halo keeps the silhouette readable over snow, sky, and other bright scenes.
        graphics.setColor(new Color(0, 0, 0, 155));
        graphics.setStroke(new BasicStroke(7.0F, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        graphics.drawArc(CENTER_X - 43, offsetY + CENTER_Y - 43, 86, 86, 0, 180);

        // Layered outer and inner metal rims.
        drawArc(graphics, offsetY, 43, 5.0F, color(0x0C0E11));
        drawArc(graphics, offsetY, 42, 3.0F, color(0x5C626B));
        drawArc(graphics, offsetY, 40, 1.0F, color(0xA4A7AA));
        drawArc(graphics, offsetY, 27, 5.0F, color(0x090B0D));
        drawArc(graphics, offsetY, 28, 2.0F, color(0x484D54));

        for (int segment = 0; segment < SEGMENTS; segment++) {
            Polygon face = segmentPolygon(segment, offsetY, OUTER_RADIUS - 2, INNER_RADIUS + 2, 0.0);
            graphics.setColor(color(0x0A0C0E));
            graphics.fillPolygon(face);
            graphics.setColor(color(0x08090B));
            graphics.setStroke(new BasicStroke(2.0F));
            graphics.drawPolygon(face);

            Polygon inset = segmentPolygon(segment, offsetY, OUTER_RADIUS - 4, INNER_RADIUS + 3, 0.45);
            graphics.setColor(segment % 2 == 0 ? color(0x2A2E33) : color(0x25292E));
            graphics.fillPolygon(inset);
            drawOuterHighlight(graphics, segment, offsetY, OUTER_RADIUS - 4, color(0x4C5158));
        }

        // Top cap and low, slab-like base make the ring read as a piece of worn machinery.
        metalPanel(graphics, CENTER_X - 7, offsetY, 14, 6);
        metalPanel(graphics, 19, offsetY + 45, 74, 11);
        graphics.setColor(color(0x171A1E));
        graphics.fillRect(24, offsetY + 47, 64, 6);
        graphics.setColor(color(0x363B42));
        graphics.drawLine(25, offsetY + 47, 87, offsetY + 47);

        drawAnchor(graphics, 6, offsetY + 42);
        drawAnchor(graphics, 88, offsetY + 42);

        // Double boost chevron; intentionally restrained until boost/overheat state animates it.
        drawChevron(graphics, offsetY + 31, color(0xB88B17));
        drawChevron(graphics, offsetY + 37, color(0xE0A91E));
    }

    private static void drawFillLayer(Graphics2D graphics, int offsetY, Color[] palette,
            int filledSegments, boolean overheated) {
        if (overheated) {
            graphics.setColor(new Color(255, 42, 26, 75));
            graphics.setStroke(new BasicStroke(6.0F, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            graphics.drawArc(CENTER_X - 42, offsetY + CENTER_Y - 42, 84, 84, 0, 180);
        }

        for (int segment = 0; segment < filledSegments; segment++) {
            Polygon face = segmentPolygon(segment, offsetY, OUTER_RADIUS - 4, INNER_RADIUS + 3, 0.45);
            graphics.setColor(palette[segment]);
            graphics.fillPolygon(face);
            graphics.setColor(darken(palette[segment], 0.55));
            graphics.setStroke(new BasicStroke(1.0F));
            graphics.drawPolygon(face);
            drawOuterHighlight(graphics, segment, offsetY, OUTER_RADIUS - 4, brighten(palette[segment], 1.18));
        }

        if (overheated) {
            drawChevron(graphics, offsetY + 31, color(0xFF3724));
            drawChevron(graphics, offsetY + 37, color(0xFF2A1D));
            graphics.setColor(color(0xFF4A23));
            graphics.fillRect(0, offsetY + 29, 4, 4);
            graphics.fillRect(5, offsetY + 23, 3, 3);
            graphics.fillRect(108, offsetY + 29, 4, 4);
            graphics.fillRect(104, offsetY + 23, 3, 3);
        }
    }

    private static void drawAnchor(Graphics2D graphics, int x, int y) {
        graphics.setColor(color(0x080A0C));
        graphics.fillRect(x, y, 18, 14);
        graphics.setColor(color(0x343940));
        graphics.fillRect(x + 2, y + 2, 14, 10);
        graphics.setColor(color(0x70757B));
        graphics.drawLine(x + 3, y + 2, x + 14, y + 2);
        graphics.setColor(color(0x15181C));
        graphics.fillRect(x + 4, y + 5, 10, 5);
        graphics.setColor(color(0xE0A81D));
        graphics.fillRect(x + 6, y + 6, 6, 2);
        graphics.setColor(color(0x0A0C0E));
        graphics.fillRect(x - 2, y + 4, 2, 6);
        graphics.fillRect(x + 18, y + 4, 2, 6);
    }

    private static void metalPanel(Graphics2D graphics, int x, int y, int width, int height) {
        graphics.setColor(color(0x0A0C0E));
        graphics.fillRect(x - 1, y, width + 2, height);
        graphics.setColor(color(0x3A3F46));
        graphics.fillRect(x, y + 1, width, height - 2);
        graphics.setColor(color(0x777C82));
        graphics.drawLine(x + 1, y + 1, x + width - 2, y + 1);
        graphics.setColor(color(0x171A1E));
        graphics.drawLine(x + 1, y + height - 2, x + width - 2, y + height - 2);
    }

    private static void drawChevron(Graphics2D graphics, int y, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(2.0F, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        graphics.drawLine(CENTER_X - 5, y + 4, CENTER_X, y - 1);
        graphics.drawLine(CENTER_X, y - 1, CENTER_X + 5, y + 4);
    }

    private static void drawArc(Graphics2D graphics, int offsetY, int radius, float width, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        graphics.drawArc(CENTER_X - radius, offsetY + CENTER_Y - radius, radius * 2, radius * 2, 0, 180);
    }

    private static void drawOuterHighlight(Graphics2D graphics, int segment, int offsetY, int radius, Color color) {
        double cell = (START_ANGLE - END_ANGLE) / SEGMENTS;
        double high = START_ANGLE - segment * cell - SEGMENT_GAP / 2.0 - 0.45;
        double low = START_ANGLE - (segment + 1) * cell + SEGMENT_GAP / 2.0 + 0.45;
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(1.0F));
        graphics.drawLine(polarX(radius, high), offsetY + polarY(radius, high),
                polarX(radius, low), offsetY + polarY(radius, low));
    }

    private static Polygon segmentPolygon(int segment, int offsetY, int outerRadius, int innerRadius,
            double insetDegrees) {
        double cell = (START_ANGLE - END_ANGLE) / SEGMENTS;
        double high = START_ANGLE - segment * cell - SEGMENT_GAP / 2.0 - insetDegrees;
        double low = START_ANGLE - (segment + 1) * cell + SEGMENT_GAP / 2.0 + insetDegrees;
        return new Polygon(
                new int[] { polarX(outerRadius, high), polarX(outerRadius, low),
                        polarX(innerRadius, low), polarX(innerRadius, high) },
                new int[] { offsetY + polarY(outerRadius, high), offsetY + polarY(outerRadius, low),
                        offsetY + polarY(innerRadius, low), offsetY + polarY(innerRadius, high) },
                4);
    }

    private static int polarX(int radius, double angleDegrees) {
        return CENTER_X + (int) Math.round(Math.cos(Math.toRadians(angleDegrees)) * radius);
    }

    private static int polarY(int radius, double angleDegrees) {
        return CENTER_Y - (int) Math.round(Math.sin(Math.toRadians(angleDegrees)) * radius);
    }

    private static Color brighten(Color color, double factor) {
        return new Color(channel(color.getRed() * factor), channel(color.getGreen() * factor),
                channel(color.getBlue() * factor), color.getAlpha());
    }

    private static Color darken(Color color, double factor) {
        return brighten(color, factor);
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static Color color(int rgb) {
        return new Color(rgb);
    }

    // The project toolchain may run on a trimmed Java desktop image without ImageIO PNG providers.
    // This tiny lossless RGBA encoder keeps the asset generator self-contained and deterministic.
    private static void writePng(BufferedImage image, File output) throws Exception {
        ByteArrayOutputStream scanlines = new ByteArrayOutputStream();
        for (int y = 0; y < image.getHeight(); y++) {
            scanlines.write(0); // PNG filter type: None
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                scanlines.write(argb >> 16 & 0xFF);
                scanlines.write(argb >> 8 & 0xFF);
                scanlines.write(argb & 0xFF);
                scanlines.write(argb >>> 24);
            }
        }

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(
                compressed, new Deflater(Deflater.BEST_COMPRESSION))) {
            scanlines.writeTo(deflater);
        }

        try (DataOutputStream png = new DataOutputStream(new FileOutputStream(output))) {
            png.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });

            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream(13);
            try (DataOutputStream header = new DataOutputStream(headerBytes)) {
                header.writeInt(image.getWidth());
                header.writeInt(image.getHeight());
                header.writeByte(8); // bit depth
                header.writeByte(6); // RGBA
                header.writeByte(0); // compression
                header.writeByte(0); // filter
                header.writeByte(0); // no interlace
            }
            writeChunk(png, "IHDR", headerBytes.toByteArray());
            writeChunk(png, "IDAT", compressed.toByteArray());
            writeChunk(png, "IEND", new byte[0]);
        }
    }

    private static void writeChunk(DataOutputStream png, String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        png.writeInt(data.length);
        png.write(typeBytes);
        png.write(data);
        png.writeInt((int) crc.getValue());
    }
}
