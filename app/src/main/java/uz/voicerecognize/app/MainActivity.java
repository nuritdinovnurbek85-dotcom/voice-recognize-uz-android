package uz.voicerecognize.app;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.PlaybackParams;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * voice.Recognize.uz - mobil ilova (Android).
 * Ilova kompyuterda ishlayotgan voice.Recognize.uz serveriga (Flask) ulanadi:
 *   /api/stt       - nutqni matnga o'tkazish (16 kHz, 16-bit, mono WAV yuboriladi)
 *   /api/translate - matnni tarjima qilish (uz / ru / en)
 *   /api/tts       - matnni nutqqa o'tkazish (audio fayl manzili qaytadi)
 *   /api/status    - server holati
 */
public class MainActivity extends Activity {

    private static final int SR = 16000;
    private static final int REQ_MIC = 101;
    private static final String[] LANG_CODES = {"uz", "ru", "en"};
    private static final String[] LANG_NAMES = {"O'zbek (UZ)", "Rus (RU)", "Ingliz (EN)"};
    private static final String[] SPEED_NAMES = {"0.5x", "0.75x", "Normal", "1.25x", "1.5x"};
    private static final float[] SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f};

    private static final int BLUE = Color.parseColor("#2A78D6");
    private static final int RED = Color.parseColor("#D64545");
    private static final int GREEN = Color.parseColor("#1BAF7A");
    private static final int GREY = Color.parseColor("#52514E");

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private EditText serverEt, srcEt, dstEt;
    private Spinner srcLangSp, dstLangSp, speedSp;
    private Button recBtn;
    private TextView statusTv;

    private volatile boolean recording = false;
    private AudioRecord recorder;
    private Thread recThread;
    private ByteArrayOutputStream pcm;
    private MediaPlayer player;

    // ------------------------------------------------------------------ UI
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("vr", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F4F6FA"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("voice.Recognize.uz");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(BLUE);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Nutq → matn • Tarjima • Matn → nutq");
        sub.setTextColor(GREY);
        root.addView(sub);

        // Server
        root.addView(label("Server manzili (kompyuterdagi dastur)"));
        LinearLayout srow = row();
        serverEt = new EditText(this);
        serverEt.setSingleLine(true);
        serverEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverEt.setHint("http://192.168.1.10:5000");
        serverEt.setText(prefs.getString("server", "http://192.168.1.10:5000"));
        srow.addView(serverEt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button checkBtn = button("Tekshirish", GREY);
        srow.addView(checkBtn);
        root.addView(srow);

        statusTv = new TextView(this);
        statusTv.setTextColor(GREY);
        statusTv.setPadding(0, dp(4), 0, dp(4));
        statusTv.setText("Holat: serverga ulanmagan");
        root.addView(statusTv);

        // Languages
        LinearLayout lrow = row();
        LinearLayout c1 = col();
        c1.addView(label("Nutq / matn tili"));
        srcLangSp = spinner(LANG_NAMES);
        c1.addView(srcLangSp);
        LinearLayout c2 = col();
        c2.addView(label("Tarjimon (qaysi tilga)"));
        dstLangSp = spinner(LANG_NAMES);
        dstLangSp.setSelection(2);
        c2.addView(dstLangSp);
        lrow.addView(c1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        lrow.addView(c2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(lrow);

        // Record
        recBtn = button("🎤  Yozish", BLUE);
        recBtn.setTextSize(18);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        rlp.setMargins(0, dp(10), 0, dp(6));
        root.addView(recBtn, rlp);

        // Source text
        root.addView(label("Matn"));
        srcEt = textArea("Gapiring yoki matn kiriting...");
        root.addView(srcEt);
        LinearLayout b1 = row();
        Button readSrc = button("🔊 O'qish", GREEN);
        Button trBtn = button("Tarjima ↓", BLUE);
        Button clrBtn = button("Tozalash", GREY);
        b1.addView(readSrc, weight());
        b1.addView(trBtn, weight());
        b1.addView(clrBtn, weight());
        root.addView(b1);

        // Translation
        root.addView(label("Tarjima"));
        dstEt = textArea("Tarjima natijasi");
        root.addView(dstEt);
        LinearLayout b2 = row();
        Button readDst = button("🔊 Tarjimani o'qish", GREEN);
        b2.addView(readDst, weight());
        root.addView(b2);

        // Speed
        LinearLayout sprow = row();
        TextView spl = label("Ijro tezligi:  ");
        sprow.addView(spl);
        speedSp = spinner(SPEED_NAMES);
        speedSp.setSelection(2);
        sprow.addView(speedSp);
        root.addView(sprow);

        TextView help = new TextView(this);
        help.setTextColor(GREY);
        help.setTextSize(12);
        help.setPadding(0, dp(12), 0, 0);
        help.setText("Eslatma: telefon va kompyuter bitta Wi-Fi tarmog'ida bo'lishi kerak. "
                + "Kompyuterda run_mobile.bat ni ishga tushiring va u ko'rsatgan manzilni yuqoriga yozing.");
        root.addView(help);

        setContentView(scroll);

        checkBtn.setOnClickListener(v -> checkServer());
        recBtn.setOnClickListener(v -> toggleRecord());
        readSrc.setOnClickListener(v -> speak(srcEt.getText().toString(), code(srcLangSp)));
        readDst.setOnClickListener(v -> speak(dstEt.getText().toString(), code(dstLangSp)));
        trBtn.setOnClickListener(v -> translate());
        clrBtn.setOnClickListener(v -> {
            srcEt.setText("");
            dstEt.setText("");
            stopPlayer();
            status("Tozalandi");
        });
        speedSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applySpeed();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        checkServer();
    }

    @Override
    protected void onDestroy() {
        recording = false;
        stopPlayer();
        pool.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ server
    private String server() {
        String s = serverEt.getText().toString().trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        prefs.edit().putString("server", s).apply();
        return s;
    }

    private void checkServer() {
        final String base = server();
        status("Server tekshirilmoqda...");
        pool.execute(() -> {
            try {
                JSONObject j = getJson(base + "/api/status");
                JSONObject stt = j.optJSONObject("stt");
                JSONObject tts = j.optJSONObject("tts");
                String msg = "Holat: ulandi ✓"
                        + (stt != null ? "  |  STT: " + stt.optString("engine") : "")
                        + (tts != null ? "  |  TTS: " + tts.optString("engine") : "");
                status(msg);
            } catch (Exception e) {
                status("Serverga ulanib bo'lmadi: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ STT
    private void toggleRecord() {
        if (recording) {
            stopRecording();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        startRecording();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                toast("Mikrofonga ruxsat berilmadi");
            }
        }
    }

    private void startRecording() {
        int min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            toast("Mikrofonni ochib bo'lmadi");
            return;
        }
        final int buf = Math.max(min, SR);
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SR,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                recorder.release();
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SR,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf);
            }
            recorder.startRecording();
        } catch (SecurityException | IllegalStateException e) {
            toast("Mikrofon xatosi: " + e.getMessage());
            return;
        }
        stopPlayer();
        pcm = new ByteArrayOutputStream();
        recording = true;
        recBtn.setText("⏹  To'xtatish");
        setBg(recBtn, RED);
        status("Yozilmoqda... gapiring");
        recThread = new Thread(() -> {
            byte[] b = new byte[buf];
            while (recording) {
                int n = recorder.read(b, 0, b.length);
                if (n > 0) pcm.write(b, 0, n);
                if (pcm.size() > SR * 2 * 120) {          // 2 daqiqadan oshsa avtomatik to'xtaydi
                    ui.post(this::stopRecording);
                    break;
                }
            }
        });
        recThread.start();
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        try {
            if (recThread != null) recThread.join(1500);
        } catch (InterruptedException ignored) {
        }
        try {
            recorder.stop();
        } catch (Exception ignored) {
        }
        recorder.release();
        recorder = null;
        recBtn.setText("🎤  Yozish");
        setBg(recBtn, BLUE);
        final byte[] wav = toWav(pcm.toByteArray());
        final String lang = code(srcLangSp);
        final String base = server();
        status("Nutq matnga o'tkazilmoqda...");
        pool.execute(() -> {
            try {
                JSONObject j = postWav(base + "/api/stt", wav, lang);
                if (!j.optBoolean("ok", false)) throw new Exception(j.optString("error", "xato"));
                final String text = j.optString("text", "");
                ui.post(() -> {
                    String old = srcEt.getText().toString().trim();
                    srcEt.setText(old.isEmpty() ? text : old + " " + text);
                });
                status("Tayyor ✓");
            } catch (Exception e) {
                status("STT xatosi: " + e.getMessage());
            }
        });
    }

    private static byte[] toWav(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 44);
        int byteRate = SR * 2;
        writeStr(out, "RIFF");
        writeInt(out, 36 + data.length);
        writeStr(out, "WAVE");
        writeStr(out, "fmt ");
        writeInt(out, 16);
        writeShort(out, 1);        // PCM
        writeShort(out, 1);        // mono
        writeInt(out, SR);
        writeInt(out, byteRate);
        writeShort(out, 2);        // block align
        writeShort(out, 16);       // bits
        writeStr(out, "data");
        writeInt(out, data.length);
        out.write(data, 0, data.length);
        return out.toByteArray();
    }

    private static void writeStr(ByteArrayOutputStream o, String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        o.write(b, 0, b.length);
    }

    private static void writeInt(ByteArrayOutputStream o, int v) {
        o.write(v & 0xff);
        o.write((v >> 8) & 0xff);
        o.write((v >> 16) & 0xff);
        o.write((v >> 24) & 0xff);
    }

    private static void writeShort(ByteArrayOutputStream o, int v) {
        o.write(v & 0xff);
        o.write((v >> 8) & 0xff);
    }

    // ------------------------------------------------------------------ Translate
    private void translate() {
        final String text = srcEt.getText().toString().trim();
        if (text.isEmpty()) {
            toast("Tarjima uchun matn kiriting");
            return;
        }
        final String from = code(srcLangSp), to = code(dstLangSp);
        if (from.equals(to)) {
            toast("Tillar bir xil tanlangan");
            return;
        }
        final String base = server();
        status("Tarjima qilinmoqda...");
        pool.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("from", from);
                body.put("to", to);
                JSONObject j = postJson(base + "/api/translate", body);
                if (!j.optBoolean("ok", false)) throw new Exception(j.optString("error", "xato"));
                final String res = j.optString("text", "");
                ui.post(() -> dstEt.setText(res));
                status("Tarjima tayyor ✓");
            } catch (Exception e) {
                status("Tarjima xatosi: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ TTS
    private void speak(final String text0, final String lang) {
        final String text = text0.trim();
        if (text.isEmpty()) {
            toast("O'qish uchun matn yo'q");
            return;
        }
        final String base = server();
        status("Nutq sintez qilinmoqda...");
        pool.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("lang", lang);
                body.put("rate", 0);
                JSONObject j = postJson(base + "/api/tts", body);
                if (!j.optBoolean("ok", false)) throw new Exception(j.optString("error", "xato"));
                final String url = base + j.optString("url");
                ui.post(() -> play(url));
            } catch (Exception e) {
                status("TTS xatosi: " + e.getMessage());
            }
        });
    }

    private void play(String url) {
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(url);
            player.setOnPreparedListener(mp -> {
                applySpeed();
                mp.start();
                status("O'qilmoqda ▶");
            });
            player.setOnCompletionListener(mp -> status("Tayyor ✓"));
            player.setOnErrorListener((mp, what, extra) -> {
                status("Audio ijro xatosi (" + what + ")");
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            status("Audio xatosi: " + e.getMessage());
        }
    }

    private void applySpeed() {
        if (player == null || Build.VERSION.SDK_INT < 23) return;
        try {
            float s = SPEEDS[speedSp.getSelectedItemPosition()];
            boolean playing = player.isPlaying();
            PlaybackParams pp = player.getPlaybackParams();
            pp.setSpeed(s);
            player.setPlaybackParams(pp);
            if (!playing) player.pause();
        } catch (Exception ignored) {
        }
    }

    private void stopPlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    // ------------------------------------------------------------------ HTTP
    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(15000);
        return readJson(c);
    }

    private static JSONObject postJson(String url, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream o = c.getOutputStream()) {
            o.write(b);
        }
        return readJson(c);
    }

    private static JSONObject postWav(String url, byte[] wav, String lang) throws Exception {
        String bd = "----vr" + System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(180000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + bd);
        try (DataOutputStream o = new DataOutputStream(c.getOutputStream())) {
            o.write(("--" + bd + "\r\nContent-Disposition: form-data; name=\"lang\"\r\n\r\n" + lang + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            o.write(("--" + bd + "\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"nutq.wav\"\r\n"
                    + "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            o.write(wav);
            o.write(("\r\n--" + bd + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        return readJson(c);
    }

    private static JSONObject readJson(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) throw new Exception("HTTP " + code);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        c.disconnect();
        String s = new String(bo.toByteArray(), StandardCharsets.UTF_8);
        try {
            return new JSONObject(s);
        } catch (Exception e) {
            throw new Exception("HTTP " + code + ": serverdan noto'g'ri javob");
        }
    }

    // ------------------------------------------------------------------ helpers
    private void status(final String s) {
        ui.post(() -> statusTv.setText(s));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private String code(Spinner sp) {
        return LANG_CODES[sp.getSelectedItemPosition()];
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.parseColor("#0B0B0B"));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, 0, dp(6), 0);
        return l;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.setMargins(dp(3), dp(6), dp(3), dp(2));
        return lp;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        return s;
    }

    private EditText textArea(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setMinLines(4);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setTextSize(16);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(8));
        g.setStroke(dp(1), Color.parseColor("#D0D4DC"));
        e.setBackground(g);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        return e;
    }

    private Button button(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        setBg(b, color);
        return b;
    }

    private void setBg(View v, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        v.setBackground(g);
    }
}
