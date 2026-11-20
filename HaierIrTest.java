import com.climhaier.app.HaierIr;
import com.climhaier.app.HaierIr.*;

/** Vérifie l'encodeur contre des trames réelles capturées (tests IRremoteESP8266). */
public class HaierIrTest {
    static int fails = 0;

    static void check(String name, String expected, byte[] got, int... ignoreIdx) {
        String[] e = expected.split(" ");
        String[] g = HaierIr.hex(got).split(" ");
        boolean ok = e.length == g.length;
        outer:
        for (int i = 0; ok && i < e.length; i++) {
            for (int ig : ignoreIdx) if (ig == i) continue outer;
            if (!e[i].equals(g[i])) ok = false;
        }
        System.out.println((ok ? "OK   " : "FAIL ") + name + "\n  attendu: " + expected + "\n  obtenu : " + HaierIr.hex(got));
        if (!ok) fails++;
    }

    public static void main(String[] a) {
        // YR-W02 réel #2 : arrêt, chaud, 30°C, ventil. forte, touche Power
        State s = new State();
        s.power = false; s.mode = HaierIr.MODE_HEAT; s.temp = 30; s.fan = HaierIr.FAN_HIGH;
        // NB : le checksum 0x75 de cette fixture est incohérent (somme réelle = 0x2B) ;
        // l'algorithme de somme est confirmé par les 3 autres trames réelles.
        check("YRW02 réel off/heat/30/high (octet 13 ignoré)", "A6 E0 00 00 00 20 00 80 00 00 00 00 05 75",
                HaierIr.encode(Protocol.YRW02_A, s, Button.POWER, 0, 0), 13);

        // YR-W02 réel #4 : froid 25, health, auto, swing auto, sommeil (octet 5 a un bit minuterie 0x08 inconnu)
        s = new State();
        s.power = true; s.mode = HaierIr.MODE_COOL; s.temp = 25; s.fan = HaierIr.FAN_AUTO;
        s.swing = true; s.sleep = true; s.health = true;
        check("YRW02 réel cool/25/auto/sleep (octets 5 & 13 ignorés)",
                "A6 9C 00 02 40 A8 00 20 80 00 00 00 0B D7",
                HaierIr.encode(Protocol.YRW02_A, s, Button.SLEEP, 0, 0), 5, 13);

        // AC176 réel : froid 24, auto, power (nibble swing 6 inconnu → octets 1 et 13 ignorés)
        s = new State();
        s.power = true; s.mode = HaierIr.MODE_COOL; s.temp = 24; s.fan = HaierIr.FAN_AUTO;
        check("AC176 réel cool/24/auto",
                "A6 86 00 00 40 A0 00 20 00 00 00 00 05 31 B7 00 00 00 00 00 00 B7",
                HaierIr.encode(Protocol.AC176, s, Button.POWER, 0, 0), 1, 13);

        // HSU07 réel : on, froid, 16°C, ventil. faible, horloge 00:01
        s = new State();
        s.power = true; s.mode = HaierIr.MODE_COOL; s.temp = 16; s.fan = HaierIr.FAN_LOW;
        check("HSU07 réel on/cool/16/low", "A5 01 20 01 00 C0 20 00 A7",
                HaierIr.encode(Protocol.HSU07, s, Button.POWER, 0, 1));

        // Motif : 4 + 14*16 + 1 durées, commence par 3000
        int[] p = HaierIr.toPattern(HaierIr.encode(Protocol.YRW02_A, s, Button.POWER, 0, 0));
        long total = 0; for (int v : p) total += v;
        boolean pok = p.length == 229 && p[0] == 3000 && p[3] == 4300 && p[228] == 520 && total < 2_000_000;
        System.out.println((pok ? "OK   " : "FAIL ") + "motif: " + p.length + " durées, " + total / 1000 + " ms");
        if (!pok) fails++;

        System.out.println(fails == 0 ? "\nTOUS LES TESTS PASSENT" : "\n" + fails + " ÉCHEC(S)");
        System.exit(fails == 0 ? 0 : 1);
    }
}
