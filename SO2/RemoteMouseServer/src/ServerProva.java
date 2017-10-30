import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by Raffaele on 27/09/2017.
 */
public class ServerProva {
    public static void main(String[] args) {
        MouseInteraction m = null;
        try {
            m = new MouseInteraction();
        } catch (AWTException e) {
            System.out.println("Impossibile inizializzare il mouse!");
        }
        JFrame frame = new JFrame();
        int x = 0;
        int y = 0;
        int offset = 1;
        String direction;
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        BufferedReader in = null;
        PrintWriter out = null;
        try {
            serverSocket = new ServerSocket(0);
            BufferedImage tmp = QR.generateQR(Inet4Address.getLocalHost().getHostAddress() + ":" + serverSocket.getLocalPort());
            frame.setPreferredSize(new Dimension(250, 280));
            frame.pack();
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.add(new JLabel(new ImageIcon(tmp)));
            frame.setVisible(true);
            System.out.println("Porta: " + serverSocket.getLocalPort());
            System.out.print("Connessione: ");
            clientSocket = serverSocket.accept();
            System.out.println("OK");
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            while (!serverSocket.isClosed()) {
                direction = in.readLine();
                offset = Integer.parseInt(direction.substring(0, 1));
                direction = direction.substring(1);
                System.out.print(offset + " ");
                System.out.println(direction);
                switch (direction) {
                    case "nn":
                        x = 0;
                        y = -offset;
                        m.moveTo(x, y, 7 - offset);
                        break;
                    case "ne":
                        x = offset;
                        y = -offset;
                        m.moveTo(x, y, 7 - offset);
                        break;
                    case "ee":
                        x = offset;
                        y = 0;
                        m.moveTo(x, y, 7 - offset);
                        break;
                    case "se":
                        x = offset;
                        y = offset;
                        m.moveTo(x, y, 7 - offset);
                        break;
                    case "ss":
                        x = 0;
                        y = offset;
                        m.moveTo(x, y, 7 - offset);
                        break;
                    case "so":
                        x = -offset;
                        y = offset;
                        m.moveTo(x, y, 7 - offset);
                        break;
                    case "oo":
                        x = -offset;
                        y = 0;
                        m.moveTo(x, y, 7 - offset);
                        break;
                    case "no":
                        x = -offset;
                        y = -offset;
                        m.moveTo(x, y, 7 - offset);
                        break;
                    case "lc":
                        m.r.mousePress(InputEvent.BUTTON1_MASK);
                        break;
                    case "rc":
                        m.r.mousePress(InputEvent.BUTTON3_MASK);
                        break;
                    case "lcr":
                        m.r.mouseRelease(InputEvent.BUTTON1_MASK);
                        break;
                    case "rcr":
                        m.r.mouseRelease(InputEvent.BUTTON3_MASK);
                        break;
                }
            }
        } catch (IOException e) {
            System.exit(-1);
        } catch (InterruptedException e) {
            System.out.println("Impossibile controllare il mouse!");
        } finally {
            try {
                in.close();
                serverSocket.close();
            } catch (IOException e) {
            }
        }
    }
}