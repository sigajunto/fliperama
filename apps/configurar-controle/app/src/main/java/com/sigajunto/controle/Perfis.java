package com.sigajunto.controle;

import android.view.InputDevice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Perfis de controle do RetroArch (autoconfig). O RetroArch dos jogos lê a pasta
 * /sdcard/Fliperama/autoconfig (joypad_autoconfig_dir no /system/jogos/retroarch.cfg)
 * e reconhece sozinho cada controle pelo nome / fabricante / modelo.
 */
final class Perfis {

    private Perfis() {}

    /**
     * Depois de um reset o /sdcard volta vazio: copia os perfis do sistema
     * (mais de 200 modelos + os que já ensinamos) que estiverem faltando.
     * Não sobrescreve nada e respeita perfis desativados (.cfg.desativado).
     */
    static synchronized int restaurar() {
        File origem = new File(Configurar.PERFIS_SISTEMA);
        File destino = new File(Configurar.PERFIS);
        File[] lista = origem.listFiles();
        if (lista == null) return 0;
        if (!destino.isDirectory() && !destino.mkdirs()) return -1;   // /sdcard ainda não liberado
        int n = 0;
        byte[] buf = new byte[8192];
        for (File f : lista) {
            if (!f.isFile() || !f.getName().endsWith(".cfg")) continue;
            File alvo = new File(destino, f.getName());
            File desativado = new File(destino, f.getName() + ".desativado");
            if (alvo.exists() || desativado.exists()) continue;
            try (InputStream in = new FileInputStream(f); OutputStream out = new FileOutputStream(alvo)) {
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                n++;
            } catch (Exception e) {
                return -1;
            }
        }
        return n;
    }

    private static String valor(Map<String, String> m, String k) {
        String v = m.get(k);
        return v == null ? "nul" : v;
    }

    /** Grava o perfil do controle ensinado e desativa outros perfis do mesmo modelo. Devolve o nome do arquivo. */
    static String gravar(InputDevice d, Map<String, String> botoes, Map<String, String> eixos) throws Exception {
        restaurar();
        String nome = d != null ? d.getName() : "Controle";
        int vid = d != null ? d.getVendorId() : 0;
        int pid = d != null ? d.getProductId() : 0;
        File pasta = new File(Configurar.PERFIS);
        if (!pasta.isDirectory() && !pasta.mkdirs())
            throw new Exception("sem acesso à pasta Fliperama (permita o acesso aos arquivos)");

        String arquivo = "meu-" + nome.replaceAll("[^A-Za-z0-9 _-]", "").trim().replace(' ', '-')
                + "-" + vid + "-" + pid + ".cfg";

        // Outros perfis do mesmo controle (mesmo nome, ou mesmo fabricante+modelo) competiriam com o nosso.
        File[] lista = pasta.listFiles();
        if (lista != null) {
            for (File f : lista) {
                if (!f.getName().endsWith(".cfg") || f.getName().equals(arquivo)) continue;
                String txt;
                try {
                    txt = ler(f);
                } catch (Exception e) {
                    continue;
                }
                boolean mesmoNome = txt.contains("input_device = \"" + nome + "\"");
                boolean mesmoId = vid != 0 && txt.contains("input_vendor_id = \"" + vid + "\"")
                        && txt.contains("input_product_id = \"" + pid + "\"");
                if (mesmoNome || mesmoId)
                    f.renameTo(new File(pasta, f.getName() + ".desativado"));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Gravado pelo app Configurar Controle\n");
        sb.append("input_driver = \"android\"\n");
        sb.append("input_device = \"").append(nome.replace("\"", "")).append("\"\n");
        if (vid != 0) {
            sb.append("input_vendor_id = \"").append(vid).append("\"\n");
            sb.append("input_product_id = \"").append(pid).append("\"\n");
        }
        String[] nomes = {"up", "down", "left", "right", "b", "a", "y", "x", "l", "r", "l2", "r2", "start", "select"};
        for (String n : nomes) {
            if (botoes.containsKey(n)) sb.append("input_").append(n).append("_btn = \"").append(botoes.get(n)).append("\"\n");
            if (eixos.containsKey(n)) sb.append("input_").append(n).append("_axis = \"").append(eixos.get(n)).append("\"\n");
        }
        // SELECT + START = sair do jogo; SELECT + botão de cima = menu do RetroArch.
        sb.append("input_enable_hotkey_btn = \"").append(valor(botoes, "select")).append("\"\n");
        sb.append("input_exit_emulator_btn = \"").append(valor(botoes, "start")).append("\"\n");
        String menu = botoes.containsKey("x") ? botoes.get("x") : botoes.get("y");
        sb.append("input_menu_toggle_btn = \"").append(menu == null ? "nul" : menu).append("\"\n");

        File tmp = new File(pasta, arquivo + ".novo");
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(tmp), Charset.forName("UTF-8"))) {
            w.write(sb.toString());
        }
        File alvo = new File(pasta, arquivo);
        alvo.delete();
        if (!tmp.renameTo(alvo)) throw new Exception("não consegui gravar o perfil");
        return arquivo;
    }

    private static String ler(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 65536)];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            return new String(b, 0, n, Charset.forName("UTF-8"));
        }
    }
}
