import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

import javax.imageio.ImageIO;

/** Lado controlado: captura a tela, envia quadros alterados e executa mouse/teclado recebidos. */
public class Server {
    static final int PORT = 5900, TILE = 64;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
        Robot robot = new Robot();
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("Aguardando viewer na porta " + port);
            while (true) {
                try (Socket s = ss.accept()) {
                    System.out.println("Conectado: " + s.getRemoteSocketAddress());
                    Thread.ofVirtual().start(() -> input(s, robot));
                    stream(s, robot, screen);
                } catch (IOException e) {
                    System.out.println("Desconectado: " + e.getMessage());
                }
            }
        }
    }

    static void stream(Socket s, Robot robot, Rectangle screen) throws IOException {
        var out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
        int cols = (screen.width + TILE - 1) / TILE, rows = (screen.height + TILE - 1) / TILE;
        int[][] prev = new int[cols * rows][];
        out.writeByte('S'); out.writeInt(screen.width); out.writeInt(screen.height);
        var png = new ByteArrayOutputStream();
        while (true) {
            BufferedImage img = robot.createScreenCapture(screen);
            for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
                int x = c * TILE, y = r * TILE, w = Math.min(TILE, screen.width - x), h = Math.min(TILE, screen.height - y);
                int[] px = img.getRGB(x, y, w, h, null, 0, w);
                int i = r * cols + c;
                if (Arrays.equals(px, prev[i])) continue;
                prev[i] = px;
                png.reset();
                ImageIO.write(img.getSubimage(x, y, w, h), "png", png);
                out.writeByte('T'); out.writeInt(x); out.writeInt(y); out.writeInt(png.size()); png.writeTo(out);
            }
            out.writeByte('F'); out.flush();
            try { Thread.sleep(100); } catch (InterruptedException e) { return; }
        }
    }

    static void input(Socket s, Robot robot) {
        try {
            var in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            while (true) {
                byte cmd = in.readByte();
                try {
                    switch (cmd) {
                        case 'M' -> robot.mouseMove(in.readInt(), in.readInt());
                        case 'P' -> robot.mousePress(InputEvent.getMaskForButton(in.readInt()));
                        case 'R' -> robot.mouseRelease(InputEvent.getMaskForButton(in.readInt()));
                        case 'W' -> robot.mouseWheel(in.readInt());
                        case 'K' -> robot.keyPress(in.readInt());
                        case 'U' -> robot.keyRelease(in.readInt());
                        default -> throw new IOException("comando inválido: " + cmd);
                    }
                } catch (IllegalArgumentException ignored) { }
            }
        } catch (IOException e) { }
    }
}
