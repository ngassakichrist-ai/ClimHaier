package com.climhaier.app;

/**
 * Encodeur infrarouge pour climatiseurs Haier (sans dépendance Android).
 *
 * Protocoles issus de la rétro-ingénierie publiée dans IRremoteESP8266
 * (src/ir_Haier.h / ir_Haier.cpp) :
 *  - YRW02_A : télécommandes Haier "V9014557-A" / YR-W02 (14 octets, modèle 0xA6)
 *  - YRW02_B : variante "V9014557-B" (14 octets, modèle 0x59)
 *  - AC176   : même trame + extension 0xB7 (22 octets)
 *  - HSU07   : ancien protocole HSU07-HEA03 (9 octets, préfixe 0xA5)
 *
 * Trame physique (identique pour les 4) : porteuse 38 kHz,
 * pré-entête 3000/3000 µs, entête 3000/4300 µs, bits MSB en premier,
 * marque 520 µs, espace 1650 µs = 1, 650 µs = 0, marque finale 520 µs.
 */
public final class HaierIr {

    private HaierIr() {}

    public static final int CARRIER_HZ = 38000;

    // Timings (µs)
    static final int PRE_MARK = 3000, PRE_SPACE = 3000;
    static final int HDR_MARK = 3000, HDR_SPACE = 4300;
    static final int BIT_MARK = 520, ONE_SPACE = 1650, ZERO_SPACE = 650;

    public enum Protocol {
        YRW02_A("Haier V9014557-A (14 octets)"),
        YRW02_B("Haier V9014557-B (14 octets)"),
        AC176("Haier 176 bits (22 octets)"),
        HSU07("Haier HSU07 ancien (9 octets)");

        public final String label;
        Protocol(String label) { this.label = label; }
    }

    // Modes génériques
    public static final int MODE_AUTO = 0, MODE_COOL = 1, MODE_DRY = 2, MODE_FAN = 3, MODE_HEAT = 4;
    // Vitesses génériques
    public static final int FAN_AUTO = 0, FAN_LOW = 1, FAN_MED = 2, FAN_HIGH = 3;

    public static final int MIN_TEMP = 16, MAX_TEMP = 30;

    /** Touche "appuyée" : ces climatiseurs reçoivent l'état complet + la touche. */
    public enum Button { POWER, MODE, TEMP_UP, TEMP_DOWN, FAN, SWING, SLEEP }

    public static final class State {
        public boolean power = false;
        public int mode = MODE_COOL;
        public int temp = 24;
        public int fan = FAN_AUTO;
        public boolean swing = false;
        public boolean sleep = false;
        public boolean health = false;
    }

    public static byte[] encode(Protocol p, State s, Button b, int hour, int minute) {
        switch (p) {
            case YRW02_A: return yrw02(s, b, 0xA6, false);
            case YRW02_B: return yrw02(s, b, 0x59, false);
            case AC176:   return yrw02(s, b, 0xA6, true);
            case HSU07:   return hsu07(s, b, hour, minute);
            default: throw new IllegalArgumentException("protocole inconnu");
        }
    }

    static int clampTemp(int t) { return Math.max(MIN_TEMP, Math.min(MAX_TEMP, t)); }

    // ---- YR-W02 / V9014557 / 176 bits -------------------------------------

    private static byte[] yrw02(State s, Button b, int model, boolean ext176) {
        int[] d = new int[ext176 ? 22 : 14];
        d[0] = model;
        int swingV = s.swing ? 0xC : 0x0;                    // 0xC = auto (balayage)
        d[1] = ((clampTemp(s.temp) - MIN_TEMP) << 4) | swingV;
        d[3] = s.health ? 0x02 : 0x00;
        d[4] = s.power ? 0x40 : 0x00;
        int fan = yrwFan(s.fan);
        d[5] = fan << 5;
        d[7] = yrwMode(s.mode) << 5;
        d[8] = s.sleep ? 0x80 : 0x00;
        d[12] = yrwButton(b) & 0x1F;
        d[13] = sum(d, 0, 13);
        if (ext176) {
            d[14] = 0xB7;
            int fan2 = (s.fan == FAN_AUTO) ? 0 : fan;        // Low=3, Med=2, High=1
            d[16] = (fan2 & 0x3) << 6;
            d[21] = sum(d, 14, 21);
        }
        return toBytes(d);
    }

    static int yrwFan(int fan) {
        switch (fan) {
            case FAN_HIGH: return 0b001;
            case FAN_MED:  return 0b010;
            case FAN_LOW:  return 0b011;
            default:       return 0b101; // auto
        }
    }

    static int yrwMode(int mode) {
        switch (mode) {
            case MODE_COOL: return 0b001;
            case MODE_DRY:  return 0b010;
            case MODE_HEAT: return 0b100;
            case MODE_FAN:  return 0b110;
            default:        return 0b000; // auto
        }
    }

    static int yrwButton(Button b) {
        switch (b) {
            case TEMP_UP:   return 0b00000;
            case TEMP_DOWN: return 0b00001;
            case SWING:     return 0b00010;
            case FAN:       return 0b00100;
            case MODE:      return 0b00110;
            case SLEEP:     return 0b01011;
            case POWER:
            default:        return 0b00101;
        }
    }

    // ---- HSU07-HEA03 (9 octets) -------------------------------------------

    private static byte[] hsu07(State s, Button b, int hour, int minute) {
        int[] d = new int[9];
        d[0] = 0xA5;
        int cmd;
        switch (b) {
            case MODE:      cmd = 0b0010; break;
            case FAN:       cmd = 0b0011; break;
            case TEMP_UP:   cmd = 0b0110; break;
            case TEMP_DOWN: cmd = 0b0111; break;
            case SLEEP:     cmd = 0b1000; break;
            case SWING:     cmd = 0b1101; break;
            case POWER:
            default:        cmd = s.power ? 0b0001 : 0b0000; break;
        }
        d[1] = cmd | ((clampTemp(s.temp) - MIN_TEMP) << 4);
        int swingV = s.swing ? 0b11 : 0b00;
        d[2] = (hour & 0x1F) | 0x20 | (swingV << 6);
        d[3] = minute & 0x3F;
        d[4] = s.health ? 0x20 : 0x00;
        int fan;
        switch (s.fan) {
            case FAN_LOW:  fan = 3; break;
            case FAN_MED:  fan = 2; break;
            case FAN_HIGH: fan = 1; break;
            default:       fan = 0; break;
        }
        d[5] = fan << 6;
        int mode;
        switch (s.mode) {
            case MODE_COOL: mode = 1; break;
            case MODE_DRY:  mode = 2; break;
            case MODE_HEAT: mode = 3; break;
            case MODE_FAN:  mode = 4; break;
            default:        mode = 0; break;
        }
        d[6] = mode << 5;
        d[7] = s.sleep ? 0x40 : 0x00;
        d[8] = sum(d, 0, 8);
        return toBytes(d);
    }

    // ---- utilitaires -------------------------------------------------------

    static int sum(int[] d, int from, int toExclusive) {
        int s = 0;
        for (int i = from; i < toExclusive; i++) s += d[i];
        return s & 0xFF;
    }

    private static byte[] toBytes(int[] d) {
        byte[] out = new byte[d.length];
        for (int i = 0; i < d.length; i++) out[i] = (byte) (d[i] & 0xFF);
        return out;
    }

    /** Motif marque/espace en microsecondes pour ConsumerIrManager.transmit(). */
    public static int[] toPattern(byte[] data) {
        int n = 4 + data.length * 16 + 1;
        int[] p = new int[n];
        int i = 0;
        p[i++] = PRE_MARK; p[i++] = PRE_SPACE;
        p[i++] = HDR_MARK; p[i++] = HDR_SPACE;
        for (byte by : data) {
            int v = by & 0xFF;
            for (int bit = 7; bit >= 0; bit--) {
                p[i++] = BIT_MARK;
                p[i++] = ((v >> bit) & 1) == 1 ? ONE_SPACE : ZERO_SPACE;
            }
        }
        p[i] = BIT_MARK;
        return p;
    }

    public static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
