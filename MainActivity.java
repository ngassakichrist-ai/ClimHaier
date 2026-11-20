package com.climhaier.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.climhaier.app.HaierIr.Protocol;

import java.util.Calendar;

public class MainActivity extends Activity {

    private static final String PREFS = "clim";
    private static final String[] MODE_NAMES = {"Auto", "Froid", "Déshumid.", "Ventilation", "Chauffage"};
    private static final String[] FAN_NAMES = {"Auto", "Faible", "Moyenne", "Forte"};

    private final HaierIr.State state = new HaierIr.State();
    private Protocol protocol = Protocol.YRW02_A;

    private ConsumerIrManager ir;
    private TextView tvTemp, tvPower, tvDetails, tvFrame;
    private Button btnProtocol;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ir = (ConsumerIrManager) getSystemService(Context.CONSUMER_IR_SERVICE);
        load();
        setContentView(buildUi());
        refresh();
        if (ir == null || !ir.hasIrEmitter()) {
            tvFrame.setText("⚠ Aucun émetteur infrarouge détecté sur ce téléphone.");
            tvFrame.setTextColor(Color.rgb(200, 40, 40));
        }
    }

    // ------------------------------------------------------------------ UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        root.setBackgroundColor(Color.rgb(240, 244, 248));

        TextView title = new TextView(this);
        title.setText("Clim Haier");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(20, 40, 70));
        root.addView(title);

        // Carte d'état
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(16), dp(20), dp(16), dp(20));
        card.setBackground(rounded(Color.WHITE));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp(16), 0, dp(16));
        root.addView(card, cardLp);

        tvPower = new TextView(this);
        tvPower.setTextSize(16);
        tvPower.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvPower);

        tvTemp = new TextView(this);
        tvTemp.setTextSize(64);
        tvTemp.setTextColor(Color.rgb(20, 40, 70));
        card.addView(tvTemp);

        tvDetails = new TextView(this);
        tvDetails.setTextSize(15);
        tvDetails.setTextColor(Color.rgb(90, 100, 120));
        tvDetails.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(tvDetails);

        // Boutons
        root.addView(row(
                button("MARCHE", Color.rgb(30, 150, 80), v -> setPower(true)),
                button("ARRÊT", Color.rgb(200, 50, 50), v -> setPower(false))));
        root.addView(row(
                button("Temp −", Color.rgb(40, 110, 200), v -> changeTemp(-1)),
                button("Temp +", Color.rgb(220, 110, 40), v -> changeTemp(+1))));
        root.addView(row(
                button("Mode", Color.rgb(80, 90, 110), v -> cycleMode()),
                button("Ventilation", Color.rgb(80, 90, 110), v -> cycleFan())));
        root.addView(row(
                button("Oscillation", Color.rgb(80, 90, 110), v -> toggleSwing()),
                button("Sommeil", Color.rgb(80, 90, 110), v -> toggleSleep())));

        btnProtocol = button("", Color.rgb(120, 120, 140), v -> cycleProtocol());
        LinearLayout.LayoutParams protoLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        protoLp.setMargins(0, dp(20), 0, 0);
        root.addView(btnProtocol, protoLp);

        TextView hint = new TextView(this);
        hint.setText("Vise la clim avec le haut du téléphone. Si MARCHE ne fait rien, "
                + "touche « Protocole » pour essayer la variante suivante, puis réessaie.");
        hint.setTextSize(13);
        hint.setTextColor(Color.rgb(90, 100, 120));
        hint.setPadding(0, dp(12), 0, dp(8));
        root.addView(hint);

        tvFrame = new TextView(this);
        tvFrame.setTextSize(11);
        tvFrame.setTypeface(Typeface.MONOSPACE);
        tvFrame.setTextColor(Color.rgb(110, 110, 130));
        root.addView(tvFrame);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private LinearLayout row(Button left, Button right) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(0, dp(64), 1f);
        lpL.setMargins(0, dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams lpR = new LinearLayout.LayoutParams(0, dp(64), 1f);
        lpR.setMargins(dp(6), dp(6), 0, dp(6));
        r.addView(left, lpL);
        r.addView(right, lpR);
        return r;
    }

    private Button button(String text, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(color));
        b.setOnClickListener(l);
        return b;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void refresh() {
        tvPower.setText(state.power ? "● EN MARCHE" : "○ ARRÊTÉE");
        tvPower.setTextColor(state.power ? Color.rgb(30, 150, 80) : Color.rgb(150, 150, 160));
        tvTemp.setText(state.temp + "°C");
        tvDetails.setText(MODE_NAMES[state.mode] + "  ·  Ventil. " + FAN_NAMES[state.fan]
                + "\nOscillation " + (state.swing ? "oui" : "non")
                + "  ·  Sommeil " + (state.sleep ? "oui" : "non"));
        btnProtocol.setText("Protocole : " + protocol.label);
    }

    // ------------------------------------------------------------- actions

    private void setPower(boolean on) {
        state.power = on;
        send(HaierIr.Button.POWER);
    }

    private void changeTemp(int delta) {
        int t = HaierIr.clampTemp(state.temp + delta);
        if (t == state.temp) return;
        state.temp = t;
        send(delta > 0 ? HaierIr.Button.TEMP_UP : HaierIr.Button.TEMP_DOWN);
    }

    private void cycleMode() {
        state.mode = (state.mode + 1) % MODE_NAMES.length;
        send(HaierIr.Button.MODE);
    }

    private void cycleFan() {
        state.fan = (state.fan + 1) % FAN_NAMES.length;
        send(HaierIr.Button.FAN);
    }

    private void toggleSwing() {
        state.swing = !state.swing;
        send(HaierIr.Button.SWING);
    }

    private void toggleSleep() {
        state.sleep = !state.sleep;
        send(HaierIr.Button.SLEEP);
    }

    private void cycleProtocol() {
        Protocol[] all = Protocol.values();
        protocol = all[(protocol.ordinal() + 1) % all.length];
        save();
        refresh();
        Toast.makeText(this, "Protocole : " + protocol.label + "\nAppuie sur MARCHE pour tester",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Envoie l'état complet. Clim arrêtée : seul POWER est transmis, les autres
     * réglages sont mémorisés et partiront avec la prochaine mise en marche.
     */
    private void send(HaierIr.Button button) {
        save();
        refresh();
        if (!state.power && button != HaierIr.Button.POWER) return;

        Calendar c = Calendar.getInstance();
        byte[] frame = HaierIr.encode(protocol, state, button,
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        tvFrame.setText("Trame envoyée (" + frame.length + " octets) :\n" + HaierIr.hex(frame));

        if (ir == null || !ir.hasIrEmitter()) {
            Toast.makeText(this, "Pas d'émetteur infrarouge sur ce téléphone", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ir.transmit(HaierIr.CARRIER_HZ, HaierIr.toPattern(frame));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Échec d'émission IR : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // --------------------------------------------------------- persistance

    private void load() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        state.power = p.getBoolean("power", false);
        state.mode = clamp(p.getInt("mode", HaierIr.MODE_COOL), 0, MODE_NAMES.length - 1);
        state.temp = HaierIr.clampTemp(p.getInt("temp", 24));
        state.fan = clamp(p.getInt("fan", HaierIr.FAN_AUTO), 0, FAN_NAMES.length - 1);
        state.swing = p.getBoolean("swing", false);
        state.sleep = p.getBoolean("sleep", false);
        try {
            protocol = Protocol.valueOf(p.getString("protocol", Protocol.YRW02_A.name()));
        } catch (IllegalArgumentException e) {
            protocol = Protocol.YRW02_A;
        }
    }

    private void save() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("power", state.power)
                .putInt("mode", state.mode)
                .putInt("temp", state.temp)
                .putInt("fan", state.fan)
                .putBoolean("swing", state.swing)
                .putBoolean("sleep", state.sleep)
                .putString("protocol", protocol.name())
                .apply();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
