import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** Lado controlador: exibe a tela remota e envia mouse/teclado. */
public class Viewer {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Server.PORT;
        Socket s = new Socket(host, port);
        var in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        var out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
        if (in.readByte() != 'S') throw new IOException("protocolo inválido");
        var img = new BufferedImage(in.readInt(), in.readInt(), BufferedImage.TYPE_INT_RGB);

        var panel = new JPanel() {
            /** Área onde a imagem é desenhada */
            Rectangle view() {
                double k = Math.min((double) getWidth() / img.getWidth(), (double) getHeight() / img.getHeight());
                int w = (int) (img.getWidth() * k), h = (int) (img.getHeight() * k);
                return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
            }
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Rectangle v = view();
                g.drawImage(img, v.x, v.y, v.width, v.height, null);
            }
            void send(char cmd, int... a) {
                try {
                    out.writeByte(cmd);
                    for (int v : a) out.writeInt(v);
                    out.flush();
                } catch (IOException e) { System.exit(0); }
            }
            void move(MouseEvent e) {
                Rectangle v = view();
                send('M', (e.getX() - v.x) * img.getWidth() / v.width, (e.getY() - v.y) * img.getHeight() / v.height);
            }
            {
                setBackground(Color.BLACK);
                setFocusable(true);
                setFocusTraversalKeysEnabled(false);
                var mouse = new MouseAdapter() {
                    @Override public void mouseMoved(MouseEvent e) { move(e); }
                    @Override public void mouseDragged(MouseEvent e) { move(e); }
                    @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); move(e); send('P', e.getButton()); }
                    @Override public void mouseReleased(MouseEvent e) { send('R', e.getButton()); }
                    @Override public void mouseWheelMoved(MouseWheelEvent e) { send('W', e.getWheelRotation()); }
                };
                addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse);
                addKeyListener(new KeyAdapter() {
                    @Override public void keyPressed(KeyEvent e) { send('K', e.getKeyCode()); }
                    @Override public void keyReleased(KeyEvent e) { send('U', e.getKeyCode()); }
                });
            }
        };

        var frame = new JFrame("VNC - " + host + ":" + port);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(panel);
        frame.setSize(img.getWidth() * 2 / 3, img.getHeight() * 2 / 3);
        frame.setVisible(true);
        panel.requestFocusInWindow();

        // recebe quadros parciais
        try {
            while (true) switch (in.readByte()) {
                case 'T' -> {
                    int x = in.readInt(), y = in.readInt();
                    var tile = ImageIO.read(new ByteArrayInputStream(in.readNBytes(in.readInt())));
                    img.getGraphics().drawImage(tile, x, y, null);
                }
                case 'F' -> panel.repaint();
                default -> throw new IOException("protocolo inválido");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Conexão encerrada: " + e.getMessage());
            System.exit(0);
        }
    }
}
