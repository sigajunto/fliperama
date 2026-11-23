package com.sigajunto.controle;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ensina o controle à multimídia: pede um botão de cada vez, aprende o código que o
 * controle manda (seja qual for a marca) e grava:
 *  - no RetroArch, como perfil do modelo do controle (vale para todos os controles iguais e todos os sistemas);
 *  - no tema do Pegasus, para qualquer botão que não seja seta abrir o jogo.
 */
public class Configurar extends Activity {

    private static final String CFG = "/system/jogos/retroarch.cfg";   // o mesmo que os jogos do Pegasus usam
    /** Pasta de perfis de controle do RetroArch (joypad_autoconfig_dir no CFG). */
    static final String PERFIS = "/sdcard/Fliperama/autoconfig/android";
    /** Perfis que vêm no sistema (sobrevivem a reset); copiados para PERFIS se faltarem. */
    static final String PERFIS_SISTEMA = "/system/jogos/autoconfig/android";
    private static final String TEMAS = "/sdcard/pegasus-frontend/themes";
    private static final String PEGASUS = "org.pegasus_frontend.android";
    private static final String RETROARCH = "com.retroarch";

    /** Passos: nome no RetroArch, texto para o usuário, se pode pular. */
    private static final String[][] PASSOS = {
            {"up", "a seta para CIMA", "nao"},
            {"down", "a seta para BAIXO", "nao"},
            {"left", "a seta para a ESQUERDA", "nao"},
            {"right", "a seta para a DIREITA", "nao"},
            {"b", "o botão de BAIXO\n(dos 4 botões da direita)", "nao"},
            {"a", "o botão da DIREITA\n(dos 4 botões da direita)", "nao"},
            {"y", "o botão da ESQUERDA\n(dos 4 botões da direita)", "sim"},
            {"x", "o botão de CIMA\n(dos 4 botões da direita)", "sim"},
            {"l", "o botão de ombro ESQUERDO (L ou L1)", "sim"},
            {"r", "o botão de ombro DIREITO (R ou R1)", "sim"},
            {"l2", "o gatilho ESQUERDO (L2), se tiver", "sim"},
            {"r2", "o gatilho DIREITO (R2), se tiver", "sim"},
            {"start", "o botão START", "nao"},
            {"select", "o botão SELECT", "nao"},
    };

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Map<String, String> botoes = new LinkedHashMap<>();   // passo -> código (btn)
    private final Map<String, String> eixos = new LinkedHashMap<>();    // passo -> eixo (axis)
    private final List<String> relatorio = new ArrayList<>();

    private TextView titulo, instrucao, detalhe;
    private LinearLayout acoes;
    private int passo = -1;              // -1 = tela inicial; PASSOS.length = fim
    private long esperarAte = 0;         // ignora entradas logo depois de aprender um botão
    private boolean eixoSolto = true;    // só aceita eixo de novo depois que voltar ao centro
    private String nomeControle = "";
    private InputDevice aparelho;          // o controle que está sendo ensinado
    private int jogador = 1;                                              // 1 a 4
    private final Map<String, Integer> donos = new HashMap<>();           // controle (descritor) -> jogador
    private final Map<String, String> botoes1 = new LinkedHashMap<>();    // cópia do controle 1
    private final Map<String, String> eixos1 = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        montarTela();
        telaInicial();
        new Thread(() -> Perfis.restaurar()).start();
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    // ------------------------------------------------------------------ tela

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void montarTela() {
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(Color.parseColor("#0E1218"));
        raiz.setPadding(dp(28), dp(20), dp(28), dp(16));

        titulo = new TextView(this);
        titulo.setTextColor(Color.parseColor("#FFB300"));
        titulo.setTextSize(24);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        raiz.addView(titulo);

        instrucao = new TextView(this);
        instrucao.setTextColor(Color.WHITE);
        instrucao.setTextSize(30);
        instrucao.setTypeface(Typeface.DEFAULT_BOLD);
        instrucao.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpI = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        instrucao.setLayoutParams(lpI);
        raiz.addView(instrucao);

        ScrollView rolagem = new ScrollView(this);
        detalhe = new TextView(this);
        detalhe.setTextColor(Color.parseColor("#A9B3C1"));
        detalhe.setTextSize(16);
        rolagem.addView(detalhe);
        rolagem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110)));
        raiz.addView(rolagem);

        acoes = new LinearLayout(this);
        acoes.setOrientation(LinearLayout.HORIZONTAL);
        acoes.setGravity(Gravity.CENTER);
        acoes.setPadding(0, dp(10), 0, 0);
        raiz.addView(acoes);

        // Nada focável: senão a primeira seta do controle só "sai do modo toque" e se perde.
        rolagem.setFocusable(false);
        raiz.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        setContentView(raiz);
    }

    private void botao(String texto, boolean destaque, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(texto);
        bt.setAllCaps(false);
        bt.setTextSize(18);
        bt.setTypeface(Typeface.DEFAULT_BOLD);
        bt.setTextColor(destaque ? Color.parseColor("#111111") : Color.parseColor("#FFB300"));
        GradientDrawable fundo = new GradientDrawable();
        fundo.setCornerRadius(dp(10));
        fundo.setColor(Color.parseColor(destaque ? "#FFB300" : "#1D2735"));
        bt.setBackground(fundo);
        bt.setPadding(dp(22), dp(12), dp(22), dp(12));
        // Os botões são só de toque: o controle fica livre para ser "ensinado".
        bt.setFocusable(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(8), 0, dp(8), 0);
        bt.setOnClickListener(l);
        acoes.addView(bt, lp);
    }

    private void telaInicial() {
        passo = -1;
        titulo.setText("Configurar Controle");
        instrucao.setText("Ligue o controle na multimídia\ne toque em Começar.");
        detalhe.setText("Mais de 200 modelos de controle já funcionam sozinhos. Se o seu não obedecer nos jogos, "
                + "ensine aqui: vou pedir um botão de cada vez e gravar nos jogos (RetroArch) e na lista (Pegasus). "
                + "Controles do mesmo modelo valem todos juntos.\n\n"
                + "Dica: SELECT + START juntos saem do jogo e voltam para a lista.");
        acoes.removeAllViews();
        botao("Começar", true, v -> comecar(1));
        botao("Ajustes do RetroArch", false, v -> abrirRetroArch());
        botao("Sair", false, v -> finish());
    }

    private void comecar(int j) {
        jogador = j;
        botoes.clear();
        eixos.clear();
        if (j == 1) {
            relatorio.clear();
            donos.clear();
        }
        // Este jogador vai ensinar de novo: o controle dele deixa de ser "de outro".
        for (java.util.Iterator<Integer> it = donos.values().iterator(); it.hasNext(); )
            if (it.next() == j) it.remove();
        relatorio.add("--- Jogador " + j + " ---");
        nomeControle = "";
        aparelho = null;
        passo = 0;
        mostrarPasso();
    }

    /** Controle 2, 3 ou 4 igual ao 1º: copia os botões sem precisar apertar tudo de novo. */
    private void igualAoPrimeiro() {
        botoes.clear();
        botoes.putAll(botoes1);
        eixos.clear();
        eixos.putAll(eixos1);
        relatorio.add("(mesmos botões do controle 1)");
        terminar();
    }

    private void mostrarPasso() {
        if (passo >= PASSOS.length) {
            terminar();
            return;
        }
        String[] p = PASSOS[passo];
        titulo.setText("Controle " + jogador + "  ·  Botão " + (passo + 1) + " de " + PASSOS.length
                + (nomeControle.isEmpty() ? "" : "  ·  " + nomeControle));
        instrucao.setText((jogador > 1 && passo == 0 ? "Pegue o controle " + jogador + ".\n" : "")
                + "Aperte " + p[1]);
        acoes.removeAllViews();
        if ("sim".equals(p[2]))
            botao("Não tenho esse botão", false, v -> { passo++; mostrarPasso(); });
        if (passo > 0)
            botao("Voltar um", false, v -> {
                passo--;
                botoes.remove(PASSOS[passo][0]);
                eixos.remove(PASSOS[passo][0]);
                mostrarPasso();
            });
        botao("Recomeçar", false, v -> comecar(jogador));
        atualizarDetalhe();
    }

    private void atualizarDetalhe() {
        StringBuilder sb = new StringBuilder();
        for (String[] p : PASSOS) {
            String c = botoes.containsKey(p[0]) ? "botão " + botoes.get(p[0])
                    : eixos.containsKey(p[0]) ? "eixo " + eixos.get(p[0]) : null;
            if (c != null)
                sb.append("✓ ").append(p[1].replace("\n", " ")).append(": ").append(c).append('\n');
        }
        detalhe.setText(sb.length() == 0 ? "Esperando o primeiro botão…" : sb.toString());
    }

    // ------------------------------------------------------------------ entrada

    private boolean capturando() {
        return passo >= 0 && passo < PASSOS.length;
    }

    private boolean doControle(InputEvent e) {
        InputDevice d = e.getDevice();
        if (d == null)
            return BuildConfig.DEBUG; // "adb shell input" no teste do emulador vem sem aparelho
        if (d.isVirtual())
            return BuildConfig.DEBUG;
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (!capturando())
            return super.dispatchKeyEvent(e);
        // Durante a captura todo botão é nosso (inclusive o "voltar" que alguns controles mandam).
        if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() > 0)
            return true;
        if (!doControle(e) || SystemClock.uptimeMillis() < esperarAte)
            return true;
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN)
            return super.dispatchKeyEvent(e);
        if (k == KeyEvent.KEYCODE_UNKNOWN) {
            detalhe.setText("Esse botão mandou um código desconhecido (scan " + e.getScanCode()
                    + "). Tente outro modo do controle (alguns têm chave X / D ou combinação para modo Android).");
            return true;
        }
        aprender(String.valueOf(k), null, e.getDevice(),
                "tecla " + k + " (" + KeyEvent.keyCodeToString(k) + ", scan " + e.getScanCode() + ")");
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (!capturando())
            return super.dispatchGenericMotionEvent(e);
        int fonte = e.getSource();
        boolean joystick = (fonte & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (fonte & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
        if (!joystick || e.getAction() != MotionEvent.ACTION_MOVE)
            return super.dispatchGenericMotionEvent(e);

        // Ordem dos eixos igual à do RetroArch para Android (android_input.c).
        int[] eixosAndroid = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_LTRIGGER,
                MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS, MotionEvent.AXIS_BRAKE};
        float maior = 0;
        int qual = -1;
        for (int i = 0; i < eixosAndroid.length; i++) {
            float v = e.getAxisValue(eixosAndroid[i]);
            if (Math.abs(v) > Math.abs(maior)) {
                maior = v;
                qual = i;
            }
        }
        if (Math.abs(maior) < 0.3f) {
            eixoSolto = true;
            return true;
        }
        if (!eixoSolto || Math.abs(maior) < 0.75f || SystemClock.uptimeMillis() < esperarAte
                || !doControle(e))
            return true;
        eixoSolto = false;

        if (qual == 4 || qual == 5) {
            // Cruz digital que chega como "chapéu" (hat): o RetroArch usa h0up/h0down/h0left/h0right.
            String dir = qual == 4 ? (maior < 0 ? "left" : "right") : (maior < 0 ? "up" : "down");
            aprender("h0" + dir, null, e.getDevice(), "chapéu " + dir);
        } else {
            String eixo = (maior < 0 ? "-" : "+") + qual;
            aprender(null, eixo, e.getDevice(), "eixo " + eixo);
        }
        return true;
    }

    private void aprender(String botao, String eixo, InputDevice d, String descricao) {
        String nome = PASSOS[passo][0];
        // Não deixa ensinar o controle de outro jogador por engano.
        boolean deTeste = d == null || (BuildConfig.DEBUG && d.isVirtual());
        if (!deTeste) {
            Integer dono = donos.get(d.getDescriptor());
            if (dono != null && dono != jogador) {
                detalhe.setText("Esse é o controle " + dono + ". Aperte no controle " + jogador + ".");
                return;
            }
            donos.put(d.getDescriptor(), jogador);
        }
        botoes.remove(nome);
        eixos.remove(nome);
        if (botao != null) botoes.put(nome, botao);
        if (eixo != null) eixos.put(nome, eixo);
        if (d != null && nomeControle.isEmpty()) {
            nomeControle = d.getName();
            aparelho = d;
            relatorio.add("Controle: " + d.getName() + "  vendor=" + d.getVendorId()
                    + " product=" + d.getProductId() + " fontes=0x" + Integer.toHexString(d.getSources()));
        }
        relatorio.add(nome + ": " + descricao);
        esperarAte = SystemClock.uptimeMillis() + 350;
        passo++;
        mostrarPasso();
    }

    // ------------------------------------------------------------------ gravar

    private void terminar() {
        passo = PASSOS.length;
        titulo.setText("Gravando…");
        instrucao.setText("");
        acoes.removeAllViews();
        StringBuilder res = new StringBuilder();
        boolean ok = true;
        try {
            String arq = Perfis.gravar(aparelho, botoes, eixos);
            res.append("✓ Jogos (RetroArch): perfil ").append(arq).append('\n');
        } catch (Exception ex) {
            ok = false;
            res.append("✗ Jogos (RetroArch): ").append(ex.getMessage()).append('\n');
        }
        try {
            int n = ajustarTemas();
            res.append(n > 0 ? "✓ Lista de jogos (Pegasus): botão de abrir ajustado\n"
                    : "• Lista de jogos (Pegasus): já estava ajustada\n");
        } catch (Exception ex) {
            ok = false;
            res.append("✗ Lista de jogos (Pegasus): ").append(ex.getMessage()).append('\n');
        }
        try {
            gravarRelatorio();
            res.append("• Relatório salvo em Documentos/controle-relatorio.txt\n");
        } catch (Exception ignorado) {
        }
        // Fecha o Pegasus (está em segundo plano) para ele ler o tema novo ao abrir.
        try {
            ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).killBackgroundProcesses(PEGASUS);
        } catch (Exception ignorado) {
        }

        if (jogador == 1) {
            botoes1.clear();
            botoes1.putAll(botoes);
            eixos1.clear();
            eixos1.putAll(eixos);
        }

        titulo.setText(ok ? "Pronto!" : "Quase…");
        instrucao.setText(ok ? "Controle configurado.\nSELECT + START sai do jogo."
                : "Algo não gravou.\nVeja abaixo.");
        detalhe.setText(res.toString());
        final int proximo = jogador + 1;
        botao("Abrir os jogos", true, v -> abrirPegasus());
        botao("Configurar outro modelo", false, v -> comecar(proximo));
        botao("Refazer este", false, v -> comecar(jogador));
        botao("Sair", false, v -> finish());
    }

    private static String ler(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), Charset.forName("UTF-8")))) {
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
        }
        return sb.toString();
    }

    private static void escrever(File f, String texto) throws Exception {
        File tmp = new File(f.getPath() + ".novo");
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(tmp), Charset.forName("UTF-8"))) {
            w.write(texto);
        }
        if (!tmp.renameTo(f)) {
            // renameTo falha se o destino existir em alguns sistemas de arquivos
            f.delete();
            if (!tmp.renameTo(f)) throw new Exception("não consegui gravar " + f.getName());
        }
    }

    /** Põe no tema "qualquer botão que não seja seta/voltar abre o jogo". Devolve quantos temas mudou. */
    private int ajustarTemas() throws Exception {
        File[] temas = new File(TEMAS).listFiles();
        if (temas == null)
            throw new Exception("pasta do Pegasus não encontrada (permita o acesso aos arquivos)");
        String marca = "function botaoDeAbrir(event)";
        String ancoraFuncao = "    function jogar() {";
        String ancoraGrade = "                if (api.keys.isAccept(event)) {\n"
                + "                    event.accepted = true;\n"
                + "                    if (!event.isAutoRepeat)\n"
                + "                        root.jogar();";
        String funcao =
                "    // Controle de qualquer marca: todo botão que não seja seta, voltar, troca de página,\n"
              + "    // volume ou mídia (botões do volante) abre o jogo. Gravado pelo app Configurar Controle.\n"
              + "    function botaoDeAbrir(event) {\n"
              + "        var k = event.key;\n"
              + "        if (k === Qt.Key_Up || k === Qt.Key_Down || k === Qt.Key_Left || k === Qt.Key_Right)\n"
              + "            return false;\n"
              + "        if (api.keys.isCancel(event) || api.keys.isPrevPage(event) || api.keys.isNextPage(event))\n"
              + "            return false;\n"
              + "        if (k >= 0x01000020 && k <= 0x01000026)   // shift, ctrl, alt...\n"
              + "            return false;\n"
              + "        if (k >= 0x01000061 && k <= 0x010000ff)   // voltar, volume, mídia, menu\n"
              + "            return false;\n"
              + "        return true;\n"
              + "    }\n\n";
        int mudou = 0;
        for (File t : temas) {
            File q = new File(t, "theme.qml");
            if (!q.isFile()) continue;
            String txt = ler(q);
            if (txt.contains(marca) || !txt.contains(ancoraFuncao) || !txt.contains(ancoraGrade)) continue;
            File copia = new File(t, "theme.qml.antes-do-controle");
            if (!copia.exists()) escrever(copia, txt);
            txt = txt.replace(ancoraFuncao, funcao + ancoraFuncao);
            txt = txt.replace(ancoraGrade, ancoraGrade.replace("api.keys.isAccept(event)",
                    "api.keys.isAccept(event) || root.botaoDeAbrir(event)"));
            escrever(q, txt);
            mudou++;
        }
        return mudou;
    }

    private void gravarRelatorio() throws Exception {
        File pasta = Environment.getExternalStoragePublicDirectory("Documents");
        if (!pasta.exists()) pasta.mkdirs();
        StringBuilder sb = new StringBuilder("Configurar Controle — ");
        sb.append(new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR")).format(new Date())).append('\n');
        for (String l : relatorio) sb.append(l).append('\n');
        escrever(new File(pasta, "controle-relatorio.txt"), sb.toString());
    }

    // ------------------------------------------------------------------ abrir apps

    private void abrirRetroArch() {
        // Sem ROM o RetroArch abre direto no menu, com a mesma configuração dos jogos.
        Intent i = new Intent();
        i.setComponent(new ComponentName(RETROARCH, "com.retroarch.browser.retroactivity.RetroActivityFuture"));
        i.putExtra("CONFIGFILE", CFG);
        i.putExtra("DATADIR", "/data/user/0/com.retroarch");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            Intent j = getPackageManager().getLaunchIntentForPackage(RETROARCH);
            if (j != null) startActivity(j);
            else detalhe.setText("RetroArch não encontrado.");
        }
    }

    private void abrirPegasus() {
        Intent i = getPackageManager().getLaunchIntentForPackage(PEGASUS);
        if (i != null) {
            startActivity(i);
            finish();
        } else {
            detalhe.setText("Pegasus não encontrado.");
        }
    }
}
