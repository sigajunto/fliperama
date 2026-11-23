# Fliperama no celular (Pegasus + RetroArch)

Fliperama montado com o **Pegasus** (tela de jogos com capas, controlada pelo controle)
e o **RetroArch** (os emuladores). Foi feito primeiro para uma multimídia de carro e aqui
está adaptado para **celular Android** (sem root).

> ⚠️ **Os jogos (ROMs) não vêm junto.** Coloque os seus próprios arquivos nas pastas de `Jogos/`.

## O que tem aqui

| Pasta | O que é |
|---|---|
| `Jogos/` | Uma pasta por sistema (18), cada uma com o `metadata.pegasus.txt` que diz ao Pegasus qual emulador abrir |
| `game_dirs.txt` | Lista das pastas de jogos para o Pegasus |
| `tema-central/` | Tema "Central" do Pegasus: sistemas à esquerda, capas à direita; **qualquer botão de ação abre o jogo** |
| `controle/autoconfig/` | Perfis de controle do RetroArch feitos por nós (shanwan Twin USB e "Controle Xbox (tradutor)") |
| `controle/*.kl` | Tabelas de botões do Android para controles genéricos (só com root) |
| `apks/` | Instalador do Pegasus (alpha16) |
| `apps/configurar-controle/` | Código do app "Configurar Controle" (Android, Java), que grava perfis de controle do RetroArch |
| `apps/xinput-tradutor/` | Código do tradutor de controle modo Xbox para aparelho sem driver `xpad` (só com root) |

## Sistemas e emuladores (núcleos)

| Pasta | Sistema | Núcleo |
|---|---|---|
| nes | Nintendinho | fceumm |
| snes | Super Nintendo | snes9x |
| megadrive, mastersystem, gamegear, sg1000 | Sega | genesis_plus_gx |
| sega32x | Sega 32X | picodrive |
| gb, gbc | Game Boy / Color | gambatte |
| gba | Game Boy Advance | mgba |
| mame | Fliperama | fbneo |
| psx | PlayStation | pcsx_rearmed |
| pcengine | PC Engine | mednafen_pce_fast |
| ngp | Neo Geo Pocket | mednafen_ngp |
| wswan | WonderSwan | mednafen_wswan |
| atari2600 | Atari 2600 | stella2014 |
| atari7800 | Atari 7800 | prosystem |
| vectrex | Vectrex | vecx |

## Montar no celular

1. **RetroArch 64 bits**: baixe em <https://www.retroarch.com/?page=platforms> (Android → "Download (AArch64)"). O pacote é `com.retroarch.aarch64`.
   Abra uma vez e, em **Atualizador online → Baixar núcleos**, baixe os núcleos da tabela acima.
   Depois, em **Atualizador online**, use também "Atualizar perfis de controle".
2. **Pegasus**: instale `apks/pegasus-fe_alpha16-82-gc3462e68_android.apk` (ou a versão mais nova em <https://pegasus-frontend.org/>). Dê permissão de arquivos.
3. Copie a pasta **`Jogos/`** para a memória interna do celular (`/storage/emulated/0/Jogos`) e ponha as ROMs em cada pasta.
4. **Tema**: copie `tema-central/` para `/storage/emulated/0/pegasus-frontend/themes/Central/`.
   No Pegasus: Ajustes → Tema → Central.
5. **Pastas de jogos**: no Pegasus, Ajustes → "Set game directories" e adicione as pastas de `Jogos/`
   (ou copie `game_dirs.txt` para `/storage/emulated/0/pegasus-frontend/`).
6. **Controle**: os perfis de `controle/autoconfig/` vão na pasta de autoconfig do RetroArch
   (Ajustes → Diretório → Perfis de controle). Controle Bluetooth/USB comum costuma funcionar direto.

Atalhos no jogo (controle 1): **SELECT + START** sai do jogo, **SELECT + botão de cima** abre o menu do RetroArch.

### Se o jogo não abrir

O `launch` de cada `metadata.pegasus.txt` aponta para:
- núcleo: `/data/data/com.retroarch.aarch64/cores/<núcleo>_libretro_android.so`
- configuração: `/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg`

Com o RetroArch de 32 bits (`com.retroarch.ra32`) ou da Play Store (`com.retroarch`), troque o nome do pacote nessas linhas.

## Licenças

- Pegasus Frontend: GPL-3.0 — código em <https://github.com/mmatyas/pegasus-frontend>
- RetroArch e núcleos: licenças próprias (GPL na maioria) — <https://github.com/libretro>
- Imagens do tema: Wikimedia Commons, ver `tema-central/CREDITOS.txt`
- Perfis de controle oficiais: <https://github.com/libretro/retroarch-joypad-autoconfig>
